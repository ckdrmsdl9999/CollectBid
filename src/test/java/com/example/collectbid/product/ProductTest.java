package com.example.collectbid.product;

import com.example.collectbid.global.error.BusinessException;
import com.example.collectbid.product.api.Category;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProductTest {
    private final ProductDtos.Save request = new ProductDtos.Save("Card", "Sealed card", Category.TCG, Product.Condition.SEALED, null);

    @Test
    void listedProductCannotBeModifiedDeletedOrReservedTwice() {
        var product = new Product(1, request, Instant.now());
        product.reserve();
        assertThatThrownBy(() -> product.update(request)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(product::delete).isInstanceOf(BusinessException.class);
        assertThatThrownBy(product::reserve).isInstanceOf(BusinessException.class);
    }

    @Test
    void unsoldProductCanBeListedAgain() {
        var product = new Product(1, request, Instant.now());
        product.reserve();
        product.release();
        product.reserve();
        assertThat(product.getStatus()).isEqualTo(Product.Status.LISTED);
    }

    @Test
    void soldProductCannotBeSoldOrReleasedAgain() {
        var product = new Product(1, request, Instant.now());
        product.reserve();
        product.completeSale();
        assertThatThrownBy(product::reserve).isInstanceOf(BusinessException.class);
        assertThatThrownBy(product::release).isInstanceOf(BusinessException.class);
        assertThatThrownBy(product::completeSale).isInstanceOf(BusinessException.class);
    }

    @Test
    void onlyOwnerMayChangeProduct() {
        assertThatThrownBy(() -> new Product(1, request, Instant.now()).requireOwner(2))
                .isInstanceOf(BusinessException.class).hasMessageContaining("권한");
    }
}
