package com.example.collectbid.auction;

import com.example.collectbid.global.security.MemberPrincipal;
import com.example.collectbid.global.web.PageResponse;
import com.example.collectbid.product.api.Category;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
class AuctionController {
    private final AuctionService commands;
    private final AuctionQueryService queries;

    @PostMapping("/forward")
    @ResponseStatus(HttpStatus.CREATED)
    AuctionDtos.View forward(@AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody AuctionDtos.CreateForward request) {
        return commands.createForward(principal.id(), request);
    }

    @PostMapping("/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    AuctionDtos.View reverse(@AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody AuctionDtos.CreateReverse request) {
        return commands.createReverse(principal.id(), request);
    }

    @GetMapping("/{id}")
    AuctionDtos.View get(@PathVariable long id) { return queries.get(id); }

    @GetMapping
    PageResponse<AuctionDtos.View> search(@RequestParam(required = false) @Size(max = 120) String keyword,
            @RequestParam(required = false) Category category, @RequestParam(required = false) Auction.Kind kind,
            @RequestParam(defaultValue = "true") boolean openOnly,
            @RequestParam(required = false) @Min(0) Long minPrice, @RequestParam(required = false) @Min(0) Long maxPrice,
            @RequestParam(defaultValue = "NEWEST") AuctionDtos.Sort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.search(keyword, category, kind, openOnly, minPrice, maxPrice, sort, page, size);
    }

    @PostMapping("/{id}/bids")
    AuctionDtos.BidView bid(@PathVariable long id, @AuthenticationPrincipal MemberPrincipal principal,
            @RequestHeader("Idempotency-Key") UUID requestKey, @Valid @RequestBody AuctionDtos.PlaceBid request) {
        return commands.bid(id, principal.id(), request.amount(), requestKey);
    }

    @GetMapping("/{id}/bids")
    PageResponse<AuctionDtos.BidView> bids(@PathVariable long id,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.bids(id, page, size);
    }

    @PostMapping("/{id}/offers")
    AuctionDtos.OfferView offer(@PathVariable long id, @AuthenticationPrincipal MemberPrincipal principal,
            @RequestHeader("Idempotency-Key") UUID requestKey, @Valid @RequestBody AuctionDtos.MakeOffer request) {
        return commands.offer(id, principal.id(), request, requestKey);
    }

    @GetMapping("/{id}/offers")
    PageResponse<AuctionDtos.OfferView> offers(@PathVariable long id, @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.offers(id, principal.id(), page, size);
    }

    @PostMapping("/{id}/offers/{offerId}/accept")
    AuctionDtos.View accept(@PathVariable long id, @PathVariable long offerId, @AuthenticationPrincipal MemberPrincipal principal) {
        return commands.acceptOffer(id, offerId, principal.id());
    }

    @PostMapping("/{id}/close")
    AuctionDtos.View close(@PathVariable long id, @AuthenticationPrincipal MemberPrincipal principal) {
        return commands.closeByOwner(id, principal.id());
    }

    @PostMapping("/{id}/cancel")
    AuctionDtos.View cancel(@PathVariable long id, @AuthenticationPrincipal MemberPrincipal principal) {
        return commands.cancel(id, principal.id());
    }
}
