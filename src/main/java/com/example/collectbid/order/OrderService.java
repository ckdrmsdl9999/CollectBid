package com.example.collectbid.order;

import com.example.collectbid.auction.api.AuctionSettled;
import com.example.collectbid.global.error.BusinessException;
import com.example.collectbid.global.web.PageResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class OrderService {
    private final OrderRepository orders;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onSettlement(AuctionSettled event) {
        // Intentionally synchronous: a failure must roll back auction and product changes, too.
        orders.save(new TradeOrder(event));
    }

    @Transactional(readOnly = true)
    public PageResponse<View> mine(long memberId, int page, int size) {
        return PageResponse.from(orders.findMine(memberId, PageRequest.of(page, size)).map(View::from));
    }

    @Transactional(readOnly = true)
    public View get(long id, long memberId) {
        var order = orders.findById(id).orElseThrow(() -> BusinessException.notFound("주문"));
        if (order.getBuyerId() != memberId && order.getSellerId() != memberId) throw BusinessException.forbidden();
        return View.from(order);
    }

    record View(long id, long auctionId, long productId, long sellerId, long buyerId,
                String productTitle, long amount, String status, Instant createdAt) {
        static View from(TradeOrder order) {
            return new View(order.getId(), order.getAuctionId(), order.getProductId(), order.getSellerId(),
                    order.getBuyerId(), order.getProductTitle(), order.getAmount(), order.getStatus(), order.getCreatedAt());
        }
    }
}
