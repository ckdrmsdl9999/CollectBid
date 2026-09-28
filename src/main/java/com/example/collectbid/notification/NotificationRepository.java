package com.example.collectbid.notification;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientIdOrderByIdDesc(long recipientId, Pageable pageable);
    Optional<Notification> findByIdAndRecipientId(long id, long recipientId);
}
