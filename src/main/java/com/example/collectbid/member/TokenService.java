package com.example.collectbid.member;

import com.example.collectbid.global.error.BusinessException;
import com.example.collectbid.global.security.MemberPrincipal;
import com.example.collectbid.global.security.TokenAuthenticator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TokenService implements TokenAuthenticator {
    private final MemberRepository members;
    private final AccessTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    TokenService(MemberRepository members, AccessTokenRepository tokens, PasswordEncoder encoder,
                 Clock clock, @Value("${collectbid.auth.token-ttl}") Duration ttl) {
        this.members = members;
        this.tokens = tokens;
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = ttl;
        this.dummyHash = encoder.encode("not-a-real-account-password");
    }

    @Transactional
    public MemberDtos.Token login(MemberDtos.Login request) {
        var member = members.findByEmail(MemberService.normalizeEmail(request.email()));
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(request.password(), member.map(Member::getPasswordHash).orElse(dummyHash))
                || member.isEmpty()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요.");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var expiresAt = clock.instant().plus(ttl);
        tokens.save(new AccessToken(hash(rawToken), member.get().getId(), expiresAt));
        return new MemberDtos.Token(rawToken, "Bearer", expiresAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberPrincipal> authenticate(String rawToken) {
        if (!rawToken.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }
        return tokens.findById(hash(rawToken)).filter(token -> token.getExpiresAt().isAfter(clock.instant()))
                .map(token -> new MemberPrincipal(token.getMemberId()));
    }

    @Transactional
    public void logout(String header) {
        tokens.deleteById(hash(header.substring(7)));
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void removeExpiredTokens() {
        tokens.deleteByExpiresAtBefore(clock.instant());
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
