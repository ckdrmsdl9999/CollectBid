package com.example.collectbid.auction;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface OfferRepository extends JpaRepository<Offer, Long> {
    Optional<Offer> findByIdAndAuctionId(long id, long auctionId);
    Optional<Offer> findByAuctionIdAndSellerId(long auctionId, long sellerId);
    Page<Offer> findByAuctionIdOrderByAmountAscIdAsc(long auctionId, Pageable pageable);
    Page<Offer> findByAuctionIdAndSellerId(long auctionId, long sellerId, Pageable pageable);
    boolean existsByAuctionId(long auctionId);
}
