package com.example.collectbid.product;

import com.example.collectbid.global.error.BusinessException;
import com.example.collectbid.global.web.PageResponse;
import com.example.collectbid.product.api.*;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ProductService implements ProductCatalog {
    private final ProductRepository products;
    private final JPAQueryFactory queries;
    private final Clock clock;

    @Transactional
    public ProductDtos.View create(long memberId, ProductDtos.Save request) {
        return ProductDtos.View.from(products.save(new Product(memberId, request, clock.instant())));
    }

    @Transactional(readOnly = true)
    public ProductDtos.View get(long id) {
        return ProductDtos.View.from(visible(id, false));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductDtos.View> search(String keyword, Category category, Long sellerId, int page, int size) {
        var product = QProduct.product;
        var predicate = new BooleanBuilder(product.status.ne(Product.Status.DELETED));
        if (keyword != null && !keyword.isBlank()) predicate.and(product.title.containsIgnoreCase(keyword.strip()));
        if (category != null) predicate.and(product.category.eq(category));
        if (sellerId != null) predicate.and(product.sellerId.eq(sellerId));
        var items = queries.selectFrom(product).where(predicate).orderBy(product.id.desc())
                .offset((long) page * size).limit(size).fetch().stream().map(ProductDtos.View::from).toList();
        Long count = queries.select(product.count()).from(product).where(predicate).fetchOne();
        return PageResponse.from(new PageImpl<>(items, PageRequest.of(page, size), count == null ? 0 : count));
    }

    @Transactional
    public ProductDtos.View update(long id, long memberId, ProductDtos.Save request) {
        var product = visible(id, true);
        product.requireOwner(memberId);
        product.update(request);
        return ProductDtos.View.from(product);
    }

    @Transactional
    public void delete(long id, long memberId) {
        var product = visible(id, true);
        product.requireOwner(memberId);
        product.delete();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductSnapshot availableOwnedProduct(long id, long sellerId) {
        var product = visible(id, false);
        product.requireOwner(sellerId);
        product.requireAvailable();
        return product.snapshot();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProductSnapshot reserve(long id, long sellerId) {
        var product = visible(id, true);
        product.requireOwner(sellerId);
        product.reserve();
        return product.snapshot();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long id) { visible(id, true).release(); }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeSale(long id) { visible(id, true).completeSale(); }

    private Product visible(long id, boolean lock) {
        return (lock ? products.findLockedById(id) : products.findById(id))
                .filter(product -> product.getStatus() != Product.Status.DELETED)
                .orElseThrow(() -> BusinessException.notFound("상품"));
    }
}
