package com.example.collectbid.member;

import com.example.collectbid.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class MemberService {
    private final MemberRepository members;
    private final PasswordEncoder encoder;
    private final Clock clock;

    @Transactional
    public MemberDtos.Profile signup(MemberDtos.Signup request) {
        String email = normalizeEmail(request.email());
        if (members.existsByEmail(email)) {
            throw BusinessException.conflict("EMAIL_IN_USE", "이미 사용 중인 이메일입니다.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw BusinessException.badRequest("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
        return MemberDtos.Profile.from(members.saveAndFlush(new Member(email,
                encoder.encode(request.password()), request.nickname().strip(), clock.instant())));
    }

    @Transactional(readOnly = true)
    public MemberDtos.Profile profile(long memberId) {
        return MemberDtos.Profile.from(members.findById(memberId)
                .orElseThrow(() -> BusinessException.notFound("회원")));
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
