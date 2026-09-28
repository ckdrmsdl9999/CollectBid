package com.example.collectbid.member;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccessTokenRepository extends JpaRepository<AccessToken, String> {
    long deleteByExpiresAtBefore(Instant cutoff);
}
