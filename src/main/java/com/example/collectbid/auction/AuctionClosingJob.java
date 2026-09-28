package com.example.collectbid.auction;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "collectbid.auction.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class AuctionClosingJob {
    private static final Logger log = LoggerFactory.getLogger(AuctionClosingJob.class);
    private final AuctionRepository auctions;
    private final AuctionService service;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${collectbid.auction.close-delay-ms:5000}")
    public void closeExpired() {
        for (Long id : auctions.findExpiredIds(clock.instant(), PageRequest.of(0, 100))) {
            try {
                service.closeExpired(id); // One transaction per auction, through the Spring proxy.
            } catch (RuntimeException exception) {
                log.error("Could not close auction {}; next scheduler run will retry", id, exception);
            }
        }
    }
}
