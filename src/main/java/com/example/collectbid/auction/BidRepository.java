package com.example.collectbid.auction;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface BidRepository extends JpaRepository<Bid, Long> {
    Optional<Bid> findByAuctionIdAndBidderIdAndRequestKey(long auctionId, long bidderId, String requestKey);
    Page<Bid> findByAuctionIdOrderByIdDesc(long auctionId, Pageable pageable);
}
