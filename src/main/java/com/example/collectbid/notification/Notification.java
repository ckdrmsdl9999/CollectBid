package com.example.collectbid.notification;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Notification {
    enum Type { OUTBID, AUCTION_WON, AUCTION_SOLD }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long recipientId;
    @Column(nullable = false)
    private Long auctionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Type type;
    @Column(nullable = false, length = 300)
    private String message;
    private Instant readAt;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    Notification(long recipientId, long auctionId, Type type, String message, Instant now) {
        this.recipientId = recipientId;
        this.auctionId = auctionId;
        this.type = type;
        this.message = message;
        this.createdAt = now;
    }

    void markRead(Instant now) { if (readAt == null) readAt = now; }
}
