package com.example.collectbid.order;

import com.example.collectbid.auction.api.AuctionSettled;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trade_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TradeOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long auctionId;
    @Column(nullable = false)
    private Long productId;
    @Column(nullable = false)
    private Long sellerId;
    @Column(nullable = false)
    private Long buyerId;
    @Column(nullable = false, length = 120)
    private String productTitle;
    @Column(nullable = false)
    private long amount;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    TradeOrder(AuctionSettled event) {
        auctionId = event.auctionId();
        productId = event.productId();
        sellerId = event.sellerId();
        buyerId = event.buyerId();
        productTitle = event.productTitle();
        amount = event.amount();
        status = "PENDING_PAYMENT";
        createdAt = event.settledAt();
    }
}
