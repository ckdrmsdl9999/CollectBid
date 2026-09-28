package com.example.collectbid.auction;

import com.example.collectbid.product.api.Category;
import com.example.collectbid.product.api.ProductSnapshot;
import jakarta.validation.constraints.*;
import java.time.Instant;

final class AuctionDtos {
    private AuctionDtos() {}
    enum Sort { NEWEST, ENDING_SOON, PRICE_ASC, PRICE_DESC }

    record CreateForward(@Positive long productId, @Min(1) @Max(Auction.MAX_AMOUNT) long startingPrice,
                         @Min(1) @Max(Auction.MAX_AMOUNT) long minIncrement, @NotNull Instant endsAt) {}
    record CreateReverse(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 3000) String description,
                         @NotNull Category category, @Min(1) @Max(Auction.MAX_AMOUNT) long budget,
                         @NotNull Instant endsAt) {}
    record PlaceBid(@Min(1) @Max(Auction.MAX_AMOUNT) long amount) {}
    record MakeOffer(@Positive long productId, @Min(1) @Max(Auction.MAX_AMOUNT) long amount,
                     @NotBlank @Size(max = 1000) String note) {}

    record View(long id, Auction.Kind kind, Auction.Status status, long ownerId, Long productId,
                String title, String description, Category category, long startingPrice, long minIncrement,
                Long currentPrice, Long leadingBidderId, Long winningOfferId, Instant endsAt,
                Instant closedAt, Instant createdAt) {
        static View from(Auction auction) {
            return new View(auction.getId(), auction.getKind(), auction.getStatus(), auction.getOwnerId(),
                    auction.getProductId(), auction.getTitle(), auction.getDescription(), auction.getCategory(),
                    auction.getStartingPrice(), auction.getMinIncrement(), auction.getCurrentPrice(),
                    auction.getLeadingBidderId(), auction.getWinningOfferId(), auction.getEndsAt(),
                    auction.getClosedAt(), auction.getCreatedAt());
        }
    }

    record BidView(long id, long auctionId, long bidderId, long amount, Instant createdAt) {
        static BidView from(Bid bid) {
            return new BidView(bid.getId(), bid.getAuctionId(), bid.getBidderId(), bid.getAmount(), bid.getCreatedAt());
        }
    }

    record OfferView(long id, long auctionId, long sellerId, long amount, String note,
                     ProductSnapshot product, Instant createdAt) {
        static OfferView from(Offer offer) {
            return new OfferView(offer.getId(), offer.getAuctionId(), offer.getSellerId(), offer.getAmount(),
                    offer.getNote(), offer.snapshot(), offer.getCreatedAt());
        }
    }
}
