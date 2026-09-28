package com.example.collectbid.product.api;

public record ProductSnapshot(long id, long sellerId, String title, String description,
                              Category category, String condition, String imageUrl) {}
