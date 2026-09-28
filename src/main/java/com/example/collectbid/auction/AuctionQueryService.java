package com.example.collectbid.auction;

import com.example.collectbid.global.error.BusinessException;
import com.example.collectbid.global.web.PageResponse;
import com.example.collectbid.product.api.Category;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AuctionQueryService {
    private final AuctionRepository auctions;
    private final BidRepository bids;
    private final OfferRepository offers;
    private final JPAQueryFactory queries;
    private final Clock clock;

    public AuctionDtos.View get(long id) { return AuctionDtos.View.from(find(id)); }

    public PageResponse<AuctionDtos.View> search(String keyword, Category category, Auction.Kind kind,
                                                boolean openOnly, Long minPrice, Long maxPrice,
                                                AuctionDtos.Sort sort, int page, int size) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw BusinessException.badRequest("최소 가격은 최대 가격보다 클 수 없습니다.");
        }
        var auction = QAuction.auction;
        var predicate = new BooleanBuilder();
        if (keyword != null && !keyword.isBlank()) predicate.and(auction.title.containsIgnoreCase(keyword.strip()));
        if (category != null) predicate.and(auction.category.eq(category));
        if (kind != null) predicate.and(auction.kind.eq(kind));
        if (openOnly) predicate.and(auction.status.eq(Auction.Status.OPEN)).and(auction.endsAt.gt(clock.instant()));
        var price = auction.currentPrice.coalesce(auction.startingPrice);
        if (minPrice != null) predicate.and(price.goe(minPrice));
        if (maxPrice != null) predicate.and(price.loe(maxPrice));
        OrderSpecifier<?> order = switch (sort) {
            case ENDING_SOON -> auction.endsAt.asc();
            case PRICE_ASC -> price.asc();
            case PRICE_DESC -> price.desc();
            case NEWEST -> auction.id.desc();
        };
        var items = queries.selectFrom(auction).where(predicate).orderBy(order, auction.id.desc())
                .offset((long) page * size).limit(size).fetch().stream().map(AuctionDtos.View::from).toList();
        Long total = queries.select(auction.count()).from(auction).where(predicate).fetchOne();
        return PageResponse.from(new PageImpl<>(items, PageRequest.of(page, size), total == null ? 0 : total));
    }

    public PageResponse<AuctionDtos.BidView> bids(long id, int page, int size) {
        find(id).requireKind(Auction.Kind.FORWARD);
        return PageResponse.from(bids.findByAuctionIdOrderByIdDesc(id, PageRequest.of(page, size)).map(AuctionDtos.BidView::from));
    }

    public PageResponse<AuctionDtos.OfferView> offers(long id, long memberId, int page, int size) {
        var auction = find(id);
        auction.requireKind(Auction.Kind.REVERSE);
        var pageable = PageRequest.of(page, size);
        // Buyer sees all proposals; sellers see only their own proposal and conditions.
        return PageResponse.from((auction.getOwnerId() == memberId
                ? offers.findByAuctionIdOrderByAmountAscIdAsc(id, pageable)
                : offers.findByAuctionIdAndSellerId(id, memberId, pageable)).map(AuctionDtos.OfferView::from));
    }

    private Auction find(long id) {
        return auctions.findById(id).orElseThrow(() -> BusinessException.notFound("경매"));
    }
}
