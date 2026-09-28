package com.example.collectbid.member;

import com.example.collectbid.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
class MemberController {
    private final MemberService members;
    private final TokenService tokens;

    @PostMapping("/api/members")
    @ResponseStatus(HttpStatus.CREATED)
    MemberDtos.Profile signup(@Valid @RequestBody MemberDtos.Signup request) {
        return members.signup(request);
    }

    @PostMapping("/api/auth/login")
    MemberDtos.Token login(@Valid @RequestBody MemberDtos.Login request) {
        return tokens.login(request);
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader("Authorization") String header) {
        tokens.logout(header);
    }

    @GetMapping("/api/members/me")
    MemberDtos.Profile me(@AuthenticationPrincipal MemberPrincipal principal) {
        return members.profile(principal.id());
    }
}
