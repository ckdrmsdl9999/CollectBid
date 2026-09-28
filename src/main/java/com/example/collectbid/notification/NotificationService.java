package com.example.collectbid.notification;

import com.example.collectbid.auction.api.AuctionSettled;
import com.example.collectbid.auction.api.HighestBidChanged;
import com.example.collectbid.global.error.BusinessException;
import com.example.collectbid.global.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class NotificationService {
    private final NotificationRepository notifications;
    private final Clock clock;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void outbid(HighestBidChanged event) {
        notifications.save(new Notification(event.previousBidderId(), event.auctionId(), Notification.Type.OUTBID,
                "최고 입찰가가 " + event.newAmount() + "원으로 변경되었습니다.", event.occurredAt()));
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void settled(AuctionSettled event) {
        notifications.save(new Notification(event.buyerId(), event.auctionId(), Notification.Type.AUCTION_WON,
                event.productTitle() + "의 구매자로 선정되었습니다.", event.settledAt()));
        notifications.save(new Notification(event.sellerId(), event.auctionId(), Notification.Type.AUCTION_SOLD,
                event.productTitle() + "의 거래가 성립되었습니다.", event.settledAt()));
    }

    @Transactional(readOnly = true)
    public PageResponse<View> mine(long recipientId, int page, int size) {
        return PageResponse.from(notifications.findByRecipientIdOrderByIdDesc(recipientId,
                PageRequest.of(page, size)).map(View::from));
    }

    @Transactional
    public View read(long id, long recipientId) {
        var notification = notifications.findByIdAndRecipientId(id, recipientId)
                .orElseThrow(() -> BusinessException.notFound("알림"));
        notification.markRead(clock.instant());
        return View.from(notification);
    }

    record View(long id, long auctionId, Notification.Type type, String message, Instant readAt, Instant createdAt) {
        static View from(Notification item) {
            return new View(item.getId(), item.getAuctionId(), item.getType(), item.getMessage(), item.getReadAt(), item.getCreatedAt());
        }
    }
}
