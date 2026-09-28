package com.example.collectbid.notification;

import com.example.collectbid.global.security.MemberPrincipal;
import com.example.collectbid.global.web.PageResponse;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
class NotificationController {
    private final NotificationService notifications;

    @GetMapping
    PageResponse<NotificationService.View> mine(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notifications.mine(principal.id(), page, size);
    }

    @PatchMapping("/{id}/read")
    NotificationService.View read(@PathVariable long id, @AuthenticationPrincipal MemberPrincipal principal) {
        return notifications.read(id, principal.id());
    }
}
