package com.example.collectbid.auction;

import com.example.collectbid.product.api.Category;
import com.example.collectbid.product.api.ProductSnapshot;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "offers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"auction_id", "seller_id"}),
        @UniqueConstraint(columnNames = {"auction_id", "seller_id", "request_key"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Offer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long auctionId;
    @Column(nullable = false)
    private Long sellerId;
    @Column(nullable = false)
    private Long productId;
    @Column(nullable = false)
    private long amount;
    @Column(nullable = false, length = 1000)
    private String note;
    @Column(nullable = false, length = 36)
    private String requestKey;
    @Column(nullable = false, length = 120)
    private String productTitle;
    @Column(nullable = false, length = 3000)
    private String productDescription;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Category category;
    @Column(nullable = false, length = 20)
    private String productCondition;
    @Column(length = 1000)
    private String imageUrl;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    Offer(long auctionId, ProductSnapshot product, long amount, String note, String requestKey, Instant now) {
        this.auctionId = auctionId;
        this.sellerId = product.sellerId();
        this.productId = product.id();
        this.amount = amount;
        this.note = note.strip();
        this.requestKey = requestKey;
        this.productTitle = product.title();
        this.productDescription = product.description();
        this.category = product.category();
        this.productCondition = product.condition();
        this.imageUrl = product.imageUrl();
        this.createdAt = now;
    }

    ProductSnapshot snapshot() {
        return new ProductSnapshot(productId, sellerId, productTitle, productDescription, category, productCondition, imageUrl);
    }
}
