package com.example.collectbid.product;

import com.example.collectbid.global.security.MemberPrincipal;
import com.example.collectbid.global.web.PageResponse;
import com.example.collectbid.product.api.Category;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
class ProductController {
    private final ProductService products;

    @GetMapping("/api/categories")
    Category[] categories() { return Category.values(); }

    @PostMapping("/api/products")
    @ResponseStatus(HttpStatus.CREATED)
    ProductDtos.View create(@AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody ProductDtos.Save request) {
        return products.create(principal.id(), request);
    }

    @GetMapping("/api/products/{id}")
    ProductDtos.View get(@PathVariable long id) { return products.get(id); }

    @GetMapping("/api/products")
    PageResponse<ProductDtos.View> search(@RequestParam(required = false) @Size(max = 120) String keyword,
                                         @RequestParam(required = false) Category category,
                                         @RequestParam(required = false) Long sellerId,
                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return products.search(keyword, category, sellerId, page, size);
    }

    @PutMapping("/api/products/{id}")
    ProductDtos.View update(@PathVariable long id, @AuthenticationPrincipal MemberPrincipal principal,
                           @Valid @RequestBody ProductDtos.Save request) {
        return products.update(id, principal.id(), request);
    }

    @DeleteMapping("/api/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id, @AuthenticationPrincipal MemberPrincipal principal) {
        products.delete(id, principal.id());
    }
}
