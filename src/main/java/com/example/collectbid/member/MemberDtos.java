package com.example.collectbid.member;

import jakarta.validation.constraints.*;
import java.time.Instant;

final class MemberDtos {
    private MemberDtos() {}

    record Signup(@NotBlank @Email @Size(max = 254) String email,
                  @NotBlank @Size(min = 10, max = 64) String password,
                  @NotBlank @Size(min = 2, max = 40) String nickname) {}

    record Login(@NotBlank @Email @Size(max = 254) String email,
                 @NotBlank @Size(max = 64) String password) {}

    record Profile(long id, String email, String nickname, Instant createdAt) {
        static Profile from(Member member) {
            return new Profile(member.getId(), member.getEmail(), member.getNickname(), member.getCreatedAt());
        }
    }

    record Token(String accessToken, String tokenType, Instant expiresAt) {}
}
