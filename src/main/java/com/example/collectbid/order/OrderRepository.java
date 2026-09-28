package com.example.collectbid.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

interface OrderRepository extends JpaRepository<TradeOrder, Long> {
    @Query("select o from TradeOrder o where o.buyerId = :memberId or o.sellerId = :memberId order by o.id desc")
    Page<TradeOrder> findMine(long memberId, Pageable pageable);
}
