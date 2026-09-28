package com.example.collectbid.auction.api;

import java.time.Instant;

public record HighestBidChanged(long auctionId, long previousBidderId, long newAmount, Instant occurredAt) {}
