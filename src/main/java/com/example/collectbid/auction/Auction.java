package com.example.collectbid.auction;

import com.example.collectbid.global.error.BusinessException;
import com.example.collectbid.product.api.Category;
import com.example.collectbid.product.api.ProductSnapshot;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Auction {
    enum Kind { FORWARD, REVERSE }
    enum Status { OPEN, CLOSED, CANCELLED }
    static final long MAX_AMOUNT = 1_000_000_000_000L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(nullable = false)
    private Long ownerId;
    private Long productId;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(nullable = false, length = 3000)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Category category;
    @Column(nullable = false)
    private long startingPrice;
    @Column(nullable = false)
    private long minIncrement;
    private Long currentPrice;
    private Long leadingBidderId;
    private Long winningOfferId;
    @Column(nullable = false)
    private Instant endsAt;
    private Instant closedAt;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private long version;

    static Auction forward(long ownerId, ProductSnapshot product, long startingPrice, long increment,
                           Instant endsAt, Instant now) {
        validateAmount(startingPrice);
        validateAmount(increment);
        var auction = create(Kind.FORWARD, ownerId, product.title(), product.description(),
                product.category(), startingPrice, endsAt, now);
        auction.productId = product.id();
        auction.minIncrement = increment;
        return auction;
    }

    static Auction reverse(long ownerId, AuctionDtos.CreateReverse request, Instant now) {
        validateAmount(request.budget());
        return create(Kind.REVERSE, ownerId, request.title().strip(), request.description().strip(),
                request.category(), request.budget(), request.endsAt(), now);
    }

    private static Auction create(Kind kind, long ownerId, String title, String description, Category category,
                                  long startingPrice, Instant endsAt, Instant now) {
        if (!endsAt.isAfter(now) || endsAt.isAfter(now.plus(Duration.ofDays(30)))) {
            throw BusinessException.badRequest("종료 시각은 현재 이후부터 30일 이내여야 합니다.");
        }
        var auction = new Auction();
        auction.kind = kind;
        auction.status = Status.OPEN;
        auction.ownerId = ownerId;
        auction.title = title;
        auction.description = description;
        auction.category = category;
        auction.startingPrice = startingPrice;
        auction.endsAt = endsAt;
        auction.createdAt = now;
        return auction;
    }

    void placeBid(long bidderId, long amount, Instant now) {
        requireKind(Kind.FORWARD);
        requireOpen(now);
        requireNotOwner(bidderId);
        validateAmount(amount);
        long minimum = currentPrice == null ? startingPrice : currentPrice + minIncrement;
        if (amount < minimum) {
            throw BusinessException.conflict("BID_TOO_LOW", "최소 입찰 금액은 " + minimum + "원입니다.");
        }
        currentPrice = amount;
        leadingBidderId = bidderId;
    }

    void validateOffer(long sellerId, long amount, Instant now) {
        requireKind(Kind.REVERSE);
        requireOpen(now);
        requireNotOwner(sellerId);
        validateAmount(amount);
        if (amount > startingPrice) throw BusinessException.badRequest("제안 금액은 구매 예산을 초과할 수 없습니다.");
    }

    void acceptOffer(Offer offer, Instant now) {
        requireKind(Kind.REVERSE);
        requireOpen(now);
        currentPrice = offer.getAmount();
        leadingBidderId = offer.getSellerId();
        winningOfferId = offer.getId();
        productId = offer.getProductId();
        close(now);
    }

    void closeExpired(Instant now) {
        if (status != Status.OPEN) return;
        if (now.isBefore(endsAt)) throw BusinessException.conflict("AUCTION_NOT_ENDED", "아직 경매 종료 시각이 아닙니다.");
        close(now);
    }

    void cancel(boolean hasOffers) {
        if (status == Status.CANCELLED) return;
        if (status != Status.OPEN || leadingBidderId != null || hasOffers) {
            throw BusinessException.conflict("CANNOT_CANCEL", "참여자가 있거나 종료된 경매는 취소할 수 없습니다.");
        }
        status = Status.CANCELLED;
    }

    void requireOwner(long memberId) { if (ownerId != memberId) throw BusinessException.forbidden(); }

    void requireKind(Kind expected) {
        if (kind != expected) throw BusinessException.badRequest("이 경매 방식에서는 지원하지 않는 작업입니다.");
    }

    private void requireOpen(Instant now) {
        if (status != Status.OPEN || !now.isBefore(endsAt)) {
            throw BusinessException.conflict("AUCTION_CLOSED", "종료되었거나 취소된 경매입니다.");
        }
    }

    private void requireNotOwner(long memberId) {
        if (ownerId == memberId) throw BusinessException.conflict("SELF_PARTICIPATION", "본인의 경매에는 참여할 수 없습니다.");
    }

    private static void validateAmount(long amount) {
        if (amount < 1 || amount > MAX_AMOUNT) throw BusinessException.badRequest("금액 범위를 확인해 주세요.");
    }

    private void close(Instant now) { status = Status.CLOSED; closedAt = now; }
}
