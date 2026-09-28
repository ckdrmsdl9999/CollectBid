package com.example.collectbid.product;

import com.example.collectbid.product.api.Category;
import jakarta.validation.constraints.*;
import java.time.Instant;

final class ProductDtos {
    private ProductDtos() {}

    record Save(@NotBlank @Size(max = 120) String title,
                @NotBlank @Size(max = 3000) String description,
                @NotNull Category category, @NotNull Product.Condition condition,
                @Size(max = 1000) @Pattern(regexp = "https://[^\\s]+", message = "HTTPS 이미지 URL을 입력해 주세요.") String imageUrl) {}

    record View(long id, long sellerId, String title, String description, Category category,
                Product.Condition condition, String imageUrl, Product.Status status, Instant createdAt) {
        static View from(Product product) {
            return new View(product.getId(), product.getSellerId(), product.getTitle(), product.getDescription(),
                    product.getCategory(), product.getCondition(), product.getImageUrl(), product.getStatus(), product.getCreatedAt());
        }
    }
}
