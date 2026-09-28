package com.example.collectbid.auction;

import com.example.collectbid.auction.api.AuctionSettled;
import com.example.collectbid.auction.api.HighestBidChanged;
import com.example.collectbid.global.error.BusinessException;
import com.example.collectbid.product.api.ProductCatalog;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AuctionService {
    private final AuctionRepository auctions;
    private final BidRepository bids;
    private final OfferRepository offers;
    private final ProductCatalog products;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public AuctionDtos.View createForward(long ownerId, AuctionDtos.CreateForward request) {
        var product = products.reserve(request.productId(), ownerId);
        var auction = Auction.forward(ownerId, product, request.startingPrice(), request.minIncrement(), request.endsAt(), clock.instant());
        return AuctionDtos.View.from(auctions.save(auction));
    }

    @Transactional
    public AuctionDtos.View createReverse(long ownerId, AuctionDtos.CreateReverse request) {
        return AuctionDtos.View.from(auctions.save(Auction.reverse(ownerId, request, clock.instant())));
    }

    @Transactional
    public AuctionDtos.BidView bid(long auctionId, long bidderId, long amount, UUID requestKey) {
        // Every bid, close and cancel takes the same auction row lock. Read time AFTER acquiring it.
        var auction = locked(auctionId);
        var existing = bids.findByAuctionIdAndBidderIdAndRequestKey(auctionId, bidderId, requestKey.toString());
        if (existing.isPresent()) {
            if (existing.get().getAmount() != amount) throw keyConflict();
            return AuctionDtos.BidView.from(existing.get());
        }
        var now = clock.instant();
        Long previousBidder = auction.getLeadingBidderId();
        auction.placeBid(bidderId, amount, now);
        var bid = bids.save(new Bid(auctionId, bidderId, amount, requestKey.toString(), now));
        if (previousBidder != null && previousBidder != bidderId) {
            events.publishEvent(new HighestBidChanged(auctionId, previousBidder, amount, now));
        }
        return AuctionDtos.BidView.from(bid);
    }

    @Transactional
    public AuctionDtos.OfferView offer(long auctionId, long sellerId, AuctionDtos.MakeOffer request, UUID requestKey) {
        var auction = locked(auctionId);
        var existing = offers.findByAuctionIdAndSellerId(auctionId, sellerId);
        if (existing.isPresent()) {
            var offer = existing.get();
            if (offer.getRequestKey().equals(requestKey.toString())) {
                if (offer.getProductId() != request.productId() || offer.getAmount() != request.amount()
                        || !offer.getNote().equals(request.note().strip())) throw keyConflict();
                return AuctionDtos.OfferView.from(offer);
            }
            throw BusinessException.conflict("OFFER_EXISTS", "한 역경매에 판매자당 하나의 제안만 등록할 수 있습니다.");
        }
        var now = clock.instant();
        auction.validateOffer(sellerId, request.amount(), now);
        var product = products.availableOwnedProduct(request.productId(), sellerId);
        if (product.category() != auction.getCategory()) throw BusinessException.badRequest("요청한 카테고리의 상품만 제안할 수 있습니다.");
        return AuctionDtos.OfferView.from(offers.save(new Offer(auctionId, product, request.amount(), request.note(), requestKey.toString(), now)));
    }

    @Transactional
    public AuctionDtos.View acceptOffer(long auctionId, long offerId, long buyerId) {
        var auction = locked(auctionId);
        auction.requireOwner(buyerId);
        auction.requireKind(Auction.Kind.REVERSE);
        // Retrying the same successful selection is harmless, including after the deadline.
        if (auction.getStatus() == Auction.Status.CLOSED && Long.valueOf(offerId).equals(auction.getWinningOfferId())) {
            return AuctionDtos.View.from(auction);
        }
        var offer = offers.findByIdAndAuctionId(offerId, auctionId).orElseThrow(() -> BusinessException.notFound("판매 제안"));
        var product = products.reserve(offer.getProductId(), offer.getSellerId());
        if (!product.equals(offer.snapshot())) {
            throw BusinessException.conflict("OFFER_STALE", "제안 이후 상품 정보가 변경되었습니다. 해당 제안은 선택할 수 없습니다.");
        }
        // Product acquisition can also wait for a lock; check the deadline after both locks.
        var now = clock.instant();
        auction.acceptOffer(offer, now);
        products.completeSale(product.id());
        events.publishEvent(new AuctionSettled(auctionId, product.id(), offer.getSellerId(), buyerId,
                product.title(), offer.getAmount(), now));
        return AuctionDtos.View.from(auction);
    }

    @Transactional
    public AuctionDtos.View closeByOwner(long id, long ownerId) {
        var auction = locked(id);
        auction.requireOwner(ownerId);
        settleExpired(auction);
        return AuctionDtos.View.from(auction);
    }

    @Transactional
    public void closeExpired(long id) { settleExpired(locked(id)); }

    @Transactional
    public AuctionDtos.View cancel(long id, long ownerId) {
        var auction = locked(id);
        auction.requireOwner(ownerId);
        boolean alreadyCancelled = auction.getStatus() == Auction.Status.CANCELLED;
        auction.cancel(offers.existsByAuctionId(id));
        if (!alreadyCancelled && auction.getKind() == Auction.Kind.FORWARD) products.release(auction.getProductId());
        return AuctionDtos.View.from(auction);
    }

    private void settleExpired(Auction auction) {
        if (auction.getStatus() != Auction.Status.OPEN) return;
        var now = clock.instant();
        auction.closeExpired(now);
        if (auction.getKind() == Auction.Kind.REVERSE) return; // Unselected reverse requests expire without a transaction.
        if (auction.getLeadingBidderId() == null) {
            products.release(auction.getProductId());
            return;
        }
        products.completeSale(auction.getProductId());
        events.publishEvent(new AuctionSettled(auction.getId(), auction.getProductId(), auction.getOwnerId(),
                auction.getLeadingBidderId(), auction.getTitle(), auction.getCurrentPrice(), now));
    }

    private Auction locked(long id) {
        return auctions.findLockedById(id).orElseThrow(() -> BusinessException.notFound("경매"));
    }

    private BusinessException keyConflict() {
        return BusinessException.conflict("IDEMPOTENCY_KEY_REUSED", "같은 요청 키를 다른 요청 내용에 사용할 수 없습니다.");
    }
}
