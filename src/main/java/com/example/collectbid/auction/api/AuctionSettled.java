package com.example.collectbid.auction.api;

import java.time.Instant;

/** Immutable cross-module contract. Delivered synchronously inside the settlement transaction. */
public record AuctionSettled(long auctionId, long productId, long sellerId, long buyerId,
                             String productTitle, long amount, Instant settledAt) {}
