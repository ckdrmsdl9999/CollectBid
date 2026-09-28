package com.example.collectbid;

import com.example.collectbid.auction.api.AuctionSettled;
import com.example.collectbid.support.MutableClock;
import java.net.URI;
import java.net.http.*;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** End-to-end HTTP, security, Flyway and transaction tests against disposable PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(CollectbidApiIT.TestBeans.class)
@Testcontainers
class CollectbidApiIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired FailingSettlementListener failure;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String PASSWORD = "Test-password-123!";

    @BeforeEach
    void resetDatabaseAndClock() {
        // This datasource is exclusively the disposable Testcontainers database, never application DB_URL.
        jdbc.execute("TRUNCATE notifications, trade_orders, offers, bids, auctions, products, access_tokens, members RESTART IDENTITY CASCADE");
        clock.set(NOW);
        failure.enabled.set(false);
    }

    @Test
    void signupLoginLogoutAndExpiryProtectPrivateEndpoints() throws Exception {
        var member = actor("buyer");
        assertThat(call("GET", "/api/members/me", member, null, null).expect(200).get("id").asLong()).isEqualTo(member.id);
        call("GET", "/api/members/me", null, null, null).expect(401);
        call("POST", "/api/members", null, Map.of("email", "BUYER@example.com", "password", PASSWORD, "nickname", "buyer"), null).expect(409);
        call("POST", "/api/auth/login", null, Map.of("email", "buyer@example.com", "password", "wrong-password"), null).expect(401);
        call("POST", "/api/auth/logout", member, null, null).expect(204);
        call("GET", "/api/members/me", member, null, null).expect(401);
        var loggedIn = login("buyer", member.id);
        clock.set(NOW.plus(Duration.ofHours(2)));
        call("GET", "/api/members/me", loggedIn, null, null).expect(401);
    }

    @Test
    void ownershipAndInputValidationAreEnforced() throws Exception {
        var seller = actor("seller");
        var stranger = actor("stranger");
        long product = product(seller, "Card");
        call("PUT", "/api/products/" + product, stranger, productBody("Changed"), null).expect(403);
        call("DELETE", "/api/products/" + product, stranger, null, null).expect(403);
        call("POST", "/api/products", null, productBody("Card"), null).expect(401);
        call("POST", "/api/products", seller, productBody(" "), null).expect(400);
        call("GET", "/api/auctions?size=101", null, null, null).expect(400);
        call("GET", "/api/auctions?minPrice=2000&maxPrice=1000", null, null, null).expect(400);
        call("GET", "/api/products?category=INVALID", null, null, null).expect(400);
        long auction = forward(seller, product);
        call("POST", "/api/auctions/" + auction + "/bids", stranger, Map.of("amount", 1000), null).expect(400);
        bid(auction, seller, 1000, UUID.randomUUID()).expect(409);
        call("POST", "/api/auctions/" + auction + "/cancel", stranger, null, null).expect(403);
        call("PUT", "/api/products/" + product, seller, productBody("Changed"), null).expect(409);
        call("DELETE", "/api/products/" + product, seller, null, null).expect(409);
        call("POST", "/api/auctions/forward", seller, forwardBody(product), null).expect(409);
    }

    @Test
    void forwardAuctionSettlesOnceAndOrdersArePrivate() throws Exception {
        var seller = actor("seller");
        var buyer = actor("buyer");
        var second = actor("second");
        long product = product(seller, "Card");
        long auction = forward(seller, product);
        var key = UUID.randomUUID();
        long bidId = bid(auction, buyer, 1000, key).expect(200).get("id").asLong();
        assertThat(bid(auction, buyer, 1000, key).expect(200).get("id").asLong()).isEqualTo(bidId);
        bid(auction, buyer, 1100, key).expect(409);
        bid(auction, second, 1050, UUID.randomUUID()).expect(409);
        bid(auction, second, 1100, UUID.randomUUID()).expect(200);
        assertThat(call("GET", "/api/notifications", buyer, null, null).expect(200).get("items").get(0).get("type").asText()).isEqualTo("OUTBID");
        call("POST", "/api/auctions/" + auction + "/close", seller, null, null).expect(409);
        call("POST", "/api/auctions/" + auction + "/cancel", seller, null, null).expect(409);
        clock.set(NOW.plusSeconds(60));
        bid(auction, buyer, 1200, UUID.randomUUID()).expect(409);
        var closes = concurrent(8, () -> call("POST", "/api/auctions/" + auction + "/close", seller, null, null));
        closes.forEach(result -> result.expect(200));
        assertThat(count("trade_orders")).isEqualTo(1);
        assertThat(count("notifications")).isEqualTo(3); // one outbid + two settlement notifications
        assertThat(call("GET", "/api/products/" + product, null, null, null).expect(200).get("status").asText()).isEqualTo("SOLD");
        var order = call("GET", "/api/orders", second, null, null).expect(200).get("items").get(0);
        assertThat(order.get("buyerId").asLong()).isEqualTo(second.id);
        assertThat(order.get("amount").asLong()).isEqualTo(1100);
        call("GET", "/api/orders/" + order.get("id").asLong(), buyer, null, null).expect(403);
        bid(auction, buyer, 1000, key).expect(200); // successful request can still be replayed after closing
        var notification = call("GET", "/api/notifications", second, null, null).expect(200).get("items").get(0);
        long notificationId = notification.get("id").asLong();
        call("PATCH", "/api/notifications/" + notificationId + "/read", buyer, null, null).expect(404);
        assertThat(call("PATCH", "/api/notifications/" + notificationId + "/read", second, null, null)
                .expect(200).get("readAt").isNull()).isFalse();
    }

    @Test
    void simultaneousEqualBidsProduceExactlyOneAcceptedBid() throws Exception {
        var seller = actor("seller");
        var buyer = actor("buyer");
        long auction = forward(seller, product(seller, "Card"));
        var results = concurrent(16, () -> bid(auction, buyer, 1000, UUID.randomUUID()));
        assertThat(results.stream().filter(response -> response.status == 200)).hasSize(1);
        assertThat(results.stream().filter(response -> response.status == 409)).hasSize(15);
        assertThat(count("bids")).isEqualTo(1);
        assertThat(call("GET", "/api/auctions/" + auction, null, null, null).expect(200).get("currentPrice").asLong()).isEqualTo(1000);
    }

    @Test
    void simultaneousRetriesOfTheSameRequestCreateOneBid() throws Exception {
        var seller = actor("seller");
        var buyer = actor("buyer");
        long auction = forward(seller, product(seller, "Card"));
        var key = UUID.randomUUID();
        var results = concurrent(12, () -> bid(auction, buyer, 1000, key));
        results.forEach(result -> result.expect(200));
        assertThat(results.stream().map(result -> result.body.get("id").asLong()).distinct()).hasSize(1);
        assertThat(count("bids")).isEqualTo(1);
    }

    @Test
    void simultaneousListingsCannotReserveTheSameProductTwice() throws Exception {
        var seller = actor("seller");
        long product = product(seller, "Card");
        var results = concurrent(8, () -> call("POST", "/api/auctions/forward", seller, forwardBody(product), null));
        assertThat(results.stream().filter(result -> result.status == 201)).hasSize(1);
        assertThat(results.stream().filter(result -> result.status == 409)).hasSize(7);
        assertThat(count("auctions")).isEqualTo(1);
    }

    @Test
    void bidWaitingForTheRowLockRechecksDeadlineAfterAcquiringIt() throws Exception {
        var seller = actor("seller");
        var buyer = actor("buyer");
        long auction = forward(seller, product(seller, "Card"));
        var executor = Executors.newSingleThreadExecutor();
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("select id from auctions where id = ? for update")) {
                statement.setLong(1, auction);
                statement.executeQuery().close();
            }
            var pending = executor.submit(() -> bid(auction, buyer, 1000, UUID.randomUUID()));
            await().atMost(Duration.ofSeconds(4)).until(() -> jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and query like '%auctions%'",
                    Long.class) > 0);
            clock.set(NOW.plusSeconds(60));
            connection.commit();
            pending.get(10, TimeUnit.SECONDS).expect(409);
            assertThat(count("bids")).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void twoCompetingReverseSelectionsOnlySellOneProduct() throws Exception {
        var buyer = actor("buyer");
        var firstSeller = actor("seller1");
        var secondSeller = actor("seller2");
        long firstProduct = product(firstSeller, "First card");
        long secondProduct = product(secondSeller, "Second card");
        long auction = reverse(buyer);
        long firstOffer = offer(auction, firstSeller, firstProduct, UUID.randomUUID()).expect(200).get("id").asLong();
        long secondOffer = offer(auction, secondSeller, secondProduct, UUID.randomUUID()).expect(200).get("id").asLong();
        var turn = new AtomicInteger();
        var results = concurrent(2, () -> {
            long offerId = turn.getAndIncrement() == 0 ? firstOffer : secondOffer;
            return call("POST", "/api/auctions/" + auction + "/offers/" + offerId + "/accept", buyer, null, null);
        });
        assertThat(results.stream().filter(result -> result.status == 200)).hasSize(1);
        assertThat(results.stream().filter(result -> result.status == 409)).hasSize(1);
        assertThat(count("trade_orders")).isEqualTo(1);
        assertThat(jdbc.queryForList("select status from products order by id", String.class))
                .containsExactlyInAnyOrder("SOLD", "AVAILABLE");
    }

    @Test
    void failedSettlementRollsBackAuctionProductOrderAndNotifications() throws Exception {
        var seller = actor("seller");
        var buyer = actor("buyer");
        long product = product(seller, "Card");
        long auction = forward(seller, product);
        bid(auction, buyer, 1000, UUID.randomUUID()).expect(200);
        clock.set(NOW.plusSeconds(60));
        failure.enabled.set(true);
        call("POST", "/api/auctions/" + auction + "/close", seller, null, null).expect(500);
        assertThat(jdbc.queryForObject("select status from auctions where id = ?", String.class, auction)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select status from products where id = ?", String.class, product)).isEqualTo("LISTED");
        assertThat(count("trade_orders")).isZero();
        assertThat(count("notifications")).isZero();
        failure.enabled.set(false);
        call("POST", "/api/auctions/" + auction + "/close", seller, null, null).expect(200);
        assertThat(count("trade_orders")).isEqualTo(1);
    }

    @Test
    void unsoldAndCancelledAuctionsReleaseTheirProducts() throws Exception {
        var seller = actor("seller");
        long product = product(seller, "Card");
        long auction = forward(seller, product);
        call("POST", "/api/auctions/" + auction + "/cancel", seller, null, null).expect(200);
        call("POST", "/api/auctions/" + auction + "/cancel", seller, null, null).expect(200);
        long relisted = forward(seller, product);
        clock.set(NOW.plusSeconds(60));
        call("POST", "/api/auctions/" + relisted + "/close", seller, null, null).expect(200);
        assertThat(call("GET", "/api/products/" + product, null, null, null).expect(200).get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(count("trade_orders")).isZero();
    }

    @Test
    void querydslFiltersSortsAndExcludesExpiredOpenRows() throws Exception {
        var seller = actor("seller");
        forward(seller, product(seller, "Pikachu"));
        forward(seller, product(seller, "Charizard"));
        var found = call("GET", "/api/auctions?keyword=pika&category=TCG&kind=FORWARD&minPrice=900&maxPrice=1100&sort=ENDING_SOON",
                null, null, null).expect(200);
        assertThat(found.get("totalElements").asLong()).isEqualTo(1);
        assertThat(found.get("items").get(0).get("title").asText()).isEqualTo("Pikachu");
        assertThat(call("GET", "/api/products?keyword=char&sellerId=" + seller.id, null, null, null)
                .expect(200).get("totalElements").asLong()).isEqualTo(1);
        clock.set(NOW.plusSeconds(60));
        assertThat(call("GET", "/api/auctions", null, null, null).expect(200).get("totalElements").asLong()).isZero();
        assertThat(call("GET", "/api/auctions?openOnly=false", null, null, null).expect(200).get("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    void reverseSelectionIsPrivateIdempotentAndCreatesCorrectBuyerSellerOrder() throws Exception {
        var buyer = actor("buyer");
        var seller = actor("seller");
        var stranger = actor("stranger");
        long product = product(seller, "Card");
        long auction = reverse(buyer);
        UUID key = UUID.randomUUID();
        long offerId = offer(auction, seller, product, key).expect(200).get("id").asLong();
        assertThat(offer(auction, seller, product, key).expect(200).get("id").asLong()).isEqualTo(offerId);
        offer(auction, seller, product, UUID.randomUUID()).expect(409);
        assertThat(call("GET", "/api/auctions/" + auction + "/offers", stranger, null, null).expect(200).get("totalElements").asLong()).isZero();
        assertThat(call("GET", "/api/auctions/" + auction + "/offers", buyer, null, null).expect(200).get("totalElements").asLong()).isEqualTo(1);
        call("POST", "/api/auctions/" + auction + "/offers/" + offerId + "/accept", seller, null, null).expect(403);
        var results = concurrent(6, () -> call("POST", "/api/auctions/" + auction + "/offers/" + offerId + "/accept", buyer, null, null));
        results.forEach(result -> result.expect(200));
        var order = call("GET", "/api/orders", buyer, null, null).expect(200).get("items").get(0);
        assertThat(order.get("sellerId").asLong()).isEqualTo(seller.id);
        assertThat(order.get("buyerId").asLong()).isEqualTo(buyer.id);
        assertThat(count("trade_orders")).isEqualTo(1);
        assertThat(count("notifications")).isEqualTo(2);
    }

    @Test
    void changedOfferProductCannotBeSelectedAndReservationRollsBack() throws Exception {
        var buyer = actor("buyer");
        var seller = actor("seller");
        long product = product(seller, "Card");
        long auction = reverse(buyer);
        long offer = offer(auction, seller, product, UUID.randomUUID()).expect(200).get("id").asLong();
        call("PUT", "/api/products/" + product, seller, productBody("Changed card"), null).expect(200);
        var result = call("POST", "/api/auctions/" + auction + "/offers/" + offer + "/accept", buyer, null, null).expect(409);
        assertThat(result.get("code").asText()).isEqualTo("OFFER_STALE");
        assertThat(jdbc.queryForObject("select status from products where id = ?", String.class, product)).isEqualTo("AVAILABLE");
        assertThat(count("trade_orders")).isZero();
    }

    @Test
    void reverseOfferCannotTakeProductAlreadyReservedByAnotherAuction() throws Exception {
        var buyer = actor("buyer");
        var seller = actor("seller");
        long product = product(seller, "Card");
        long auction = reverse(buyer);
        long offer = offer(auction, seller, product, UUID.randomUUID()).expect(200).get("id").asLong();
        forward(seller, product);
        call("POST", "/api/auctions/" + auction + "/offers/" + offer + "/accept", buyer, null, null).expect(409);
        assertThat(count("trade_orders")).isZero();
    }

    @Test
    void reverseExpiryRejectsSelectionAndDoesNotAutoChooseLowestOffer() throws Exception {
        var buyer = actor("buyer");
        var seller = actor("seller");
        long product = product(seller, "Card");
        long auction = reverse(buyer);
        long offer = offer(auction, seller, product, UUID.randomUUID()).expect(200).get("id").asLong();
        clock.set(NOW.plusSeconds(60));
        call("POST", "/api/auctions/" + auction + "/offers/" + offer + "/accept", buyer, null, null).expect(409);
        call("POST", "/api/auctions/" + auction + "/close", buyer, null, null).expect(200);
        assertThat(count("trade_orders")).isZero();
        assertThat(jdbc.queryForObject("select status from products where id = ?", String.class, product)).isEqualTo("AVAILABLE");
    }

    private Actor actor(String name) throws Exception {
        var profile = call("POST", "/api/members", null, Map.of("email", name + "@example.com", "password", PASSWORD, "nickname", name), null).expect(201);
        assertThat(profile.has("passwordHash")).isFalse();
        return login(name, profile.get("id").asLong());
    }

    private Actor login(String name, long id) throws Exception {
        var token = call("POST", "/api/auth/login", null, Map.of("email", name + "@example.com", "password", PASSWORD), null).expect(200);
        String raw = token.get("accessToken").asText();
        assertThat(jdbc.queryForList("select token_hash from access_tokens", String.class)).doesNotContain(raw);
        return new Actor(id, raw);
    }

    private Map<String, Object> productBody(String title) {
        return Map.of("title", title, "description", "Sealed collectible", "category", "TCG", "condition", "SEALED");
    }

    private long product(Actor seller, String title) throws Exception {
        return call("POST", "/api/products", seller, productBody(title), null).expect(201).get("id").asLong();
    }

    private Map<String, Object> forwardBody(long product) {
        return Map.of("productId", product, "startingPrice", 1000, "minIncrement", 100, "endsAt", NOW.plusSeconds(60).toString());
    }

    private long forward(Actor seller, long product) throws Exception {
        return call("POST", "/api/auctions/forward", seller, forwardBody(product), null).expect(201).get("id").asLong();
    }

    private long reverse(Actor buyer) throws Exception {
        return call("POST", "/api/auctions/reverse", buyer, Map.of("title", "Card wanted", "description", "Sealed card wanted",
                "category", "TCG", "budget", 5000, "endsAt", NOW.plusSeconds(60).toString()), null).expect(201).get("id").asLong();
    }

    private ApiResponse bid(long auction, Actor buyer, long amount, UUID key) throws Exception {
        return call("POST", "/api/auctions/" + auction + "/bids", buyer, Map.of("amount", amount), key);
    }

    private ApiResponse offer(long auction, Actor seller, long product, UUID key) throws Exception {
        return call("POST", "/api/auctions/" + auction + "/offers", seller,
                Map.of("productId", product, "amount", 3000, "note", "Sealed, shipping included"), key);
    }

    private ApiResponse call(String method, String path, Actor actor, Object body, UUID requestKey) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(20));
        if (actor != null) request.header("Authorization", "Bearer " + actor.token);
        if (requestKey != null) request.header("Idempotency-Key", requestKey.toString());
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body()));
    }

    private List<ApiResponse> concurrent(int count, Callable<ApiResponse> task) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        try {
            List<Future<ApiResponse>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                return task.call();
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<ApiResponse> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }

    record Actor(long id, String token) {}
    record ApiResponse(int status, JsonNode body) {
        JsonNode expect(int expected) {
            assertThat(status).as("Response: %s", body).isEqualTo(expected);
            return body;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean FailingSettlementListener failingSettlementListener() { return new FailingSettlementListener(); }
    }

    static class FailingSettlementListener {
        final AtomicBoolean enabled = new AtomicBoolean();
        @EventListener
        public void fail(AuctionSettled event) {
            if (enabled.get()) throw new IllegalStateException("Intentional settlement failure for rollback verification");
        }
    }
}
