package com.example.collectbid.auction;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bids", uniqueConstraints = @UniqueConstraint(columnNames = {"auction_id", "bidder_id", "request_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Bid {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long auctionId;
    @Column(nullable = false)
    private Long bidderId;
    @Column(nullable = false)
    private long amount;
    @Column(nullable = false, length = 36)
    private String requestKey;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    Bid(long auctionId, long bidderId, long amount, String requestKey, Instant now) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.requestKey = requestKey;
        this.createdAt = now;
    }
}
