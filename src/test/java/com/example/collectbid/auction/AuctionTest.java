package com.example.collectbid.auction;

import com.example.collectbid.global.error.BusinessException;
import com.example.collectbid.product.api.Category;
import com.example.collectbid.product.api.ProductSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuctionTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = NOW.plusSeconds(60);
    private final ProductSnapshot product = new ProductSnapshot(10, 1, "Card", "Sealed card", Category.TCG, "SEALED", null);

    private Auction auction() { return Auction.forward(1, product, 10_000, 1_000, END, NOW); }

    @Test
    void firstBidCanEqualStartingPriceAndNextBidMustRespectIncrement() {
        var auction = auction();
        auction.placeBid(2, 10_000, NOW);
        assertThatThrownBy(() -> auction.placeBid(3, 10_500, NOW)).isInstanceOf(BusinessException.class);
        auction.placeBid(3, 11_000, NOW);
        assertThat(auction.getCurrentPrice()).isEqualTo(11_000);
        assertThat(auction.getLeadingBidderId()).isEqualTo(3);
    }

    @Test
    void sellerCannotBid() {
        assertThatThrownBy(() -> auction().placeBid(1, 20_000, NOW)).hasMessageContaining("본인");
    }

    @Test
    void exactDeadlineRejectsBidButAllowsClosing() {
        var auction = auction();
        assertThatThrownBy(() -> auction.placeBid(2, 10_000, END)).hasMessageContaining("종료");
        auction.closeExpired(END);
        assertThat(auction.getStatus()).isEqualTo(Auction.Status.CLOSED);
    }

    @Test
    void cannotCloseBeforeDeadline() {
        assertThatThrownBy(() -> auction().closeExpired(END.minusNanos(1))).hasMessageContaining("아직");
    }

    @Test
    void closeIsIdempotentAndKeepsOriginalTimestamp() {
        var auction = auction();
        auction.closeExpired(END);
        auction.closeExpired(END.plusSeconds(10));
        assertThat(auction.getClosedAt()).isEqualTo(END);
    }

    @Test
    void auctionWithBidsCannotBeCancelled() {
        var auction = auction();
        auction.placeBid(2, 10_000, NOW);
        assertThatThrownBy(() -> auction.cancel(false)).hasMessageContaining("취소");
    }

    @Test
    void maximumPriceCannotOverflowIntoAnAcceptedBid() {
        var auction = Auction.forward(1, product, Auction.MAX_AMOUNT, Auction.MAX_AMOUNT, END, NOW);
        auction.placeBid(2, Auction.MAX_AMOUNT, NOW);
        assertThatThrownBy(() -> auction.placeBid(3, Auction.MAX_AMOUNT, NOW)).hasMessageContaining("최소 입찰");
        assertThatThrownBy(() -> auction.placeBid(3, Long.MAX_VALUE, NOW)).hasMessageContaining("금액 범위");
    }

    @Test
    void reverseAuctionChecksBudgetAndParticipationType() {
        var auction = Auction.reverse(1, new AuctionDtos.CreateReverse("Card wanted", "Sealed", Category.TCG, 50_000, END), NOW);
        auction.validateOffer(2, 40_000, NOW);
        assertThatThrownBy(() -> auction.validateOffer(2, 50_001, NOW)).hasMessageContaining("예산");
        assertThatThrownBy(() -> auction.validateOffer(1, 40_000, NOW)).hasMessageContaining("본인");
        assertThatThrownBy(() -> auction.placeBid(2, 40_000, NOW)).hasMessageContaining("방식");
        assertThatThrownBy(() -> auction.cancel(true)).hasMessageContaining("참여자");
    }

    @Test
    void pastAndUnreasonablyDistantDeadlinesAreRejected() {
        assertThatThrownBy(() -> Auction.forward(1, product, 1_000, 100, NOW, NOW)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Auction.forward(1, product, 1_000, 100, NOW.plusSeconds(31 * 86400L), NOW))
                .isInstanceOf(BusinessException.class);
    }
}
