package com.example.collectbid.product;

import com.example.collectbid.global.error.BusinessException;
import com.example.collectbid.product.api.Category;
import com.example.collectbid.product.api.ProductSnapshot;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Product {
    enum Condition { SEALED, LIKE_NEW, GOOD, FAIR }
    enum Status { AVAILABLE, LISTED, SOLD, DELETED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long sellerId;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(nullable = false, length = 3000)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Category category;
    @Enumerated(EnumType.STRING) @Column(name = "item_condition", nullable = false, length = 20)
    private Condition condition;
    @Column(length = 1000)
    private String imageUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private long version;

    Product(long sellerId, ProductDtos.Save request, Instant now) {
        this.sellerId = sellerId;
        this.status = Status.AVAILABLE;
        this.createdAt = now;
        update(request);
    }

    void requireOwner(long memberId) {
        if (sellerId != memberId) throw BusinessException.forbidden();
    }

    void requireAvailable() {
        if (status != Status.AVAILABLE) {
            throw BusinessException.conflict("PRODUCT_UNAVAILABLE", "출품 중이거나 거래가 끝난 상품은 변경하거나 출품할 수 없습니다.");
        }
    }

    void update(ProductDtos.Save request) {
        requireAvailable();
        title = request.title().strip();
        description = request.description().strip();
        category = request.category();
        condition = request.condition();
        imageUrl = request.imageUrl();
    }

    void reserve() { requireAvailable(); status = Status.LISTED; }
    void delete() { requireAvailable(); status = Status.DELETED; }

    void release() {
        requireListed();
        status = Status.AVAILABLE;
    }

    void completeSale() {
        requireListed();
        status = Status.SOLD;
    }

    private void requireListed() {
        if (status != Status.LISTED) throw BusinessException.conflict("PRODUCT_NOT_LISTED", "출품 상태가 아닙니다.");
    }

    ProductSnapshot snapshot() {
        return new ProductSnapshot(id, sellerId, title, description, category, condition.name(), imageUrl);
    }
}
