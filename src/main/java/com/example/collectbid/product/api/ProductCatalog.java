package com.example.collectbid.product.api;

/** The only product operations other modules may call. Never returns a managed entity. */
public interface ProductCatalog {
    ProductSnapshot availableOwnedProduct(long productId, long sellerId);
    ProductSnapshot reserve(long productId, long sellerId);
    void release(long productId);
    void completeSale(long productId);
}
