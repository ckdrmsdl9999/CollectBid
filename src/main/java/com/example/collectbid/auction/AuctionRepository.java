package com.example.collectbid.auction;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

interface AuctionRepository extends JpaRepository<Auction, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findLockedById(long id);

    @Query("select a.id from Auction a where a.status = 'OPEN' and a.endsAt <= :now order by a.endsAt, a.id")
    List<Long> findExpiredIds(Instant now, Pageable pageable);
}
