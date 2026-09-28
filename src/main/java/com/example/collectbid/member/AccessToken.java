package com.example.collectbid.member;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "access_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AccessToken {
    @Id @Column(length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Long memberId;
    @Column(nullable = false)
    private Instant expiresAt;

    AccessToken(String hash, long memberId, Instant expiresAt) {
        this.tokenHash = hash;
        this.memberId = memberId;
        this.expiresAt = expiresAt;
    }
}
