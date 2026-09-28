package com.example.collectbid.order;

import com.example.collectbid.global.security.MemberPrincipal;
import com.example.collectbid.global.web.PageResponse;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
class OrderController {
    private final OrderService orders;

    @GetMapping
    PageResponse<OrderService.View> mine(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.mine(principal.id(), page, size);
    }

    @GetMapping("/{id}")
    OrderService.View get(@PathVariable long id, @AuthenticationPrincipal MemberPrincipal principal) {
        return orders.get(id, principal.id());
    }
}
