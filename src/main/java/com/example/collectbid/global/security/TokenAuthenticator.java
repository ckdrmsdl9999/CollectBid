package com.example.collectbid.global.security;

import java.util.Optional;

public interface TokenAuthenticator {
    Optional<MemberPrincipal> authenticate(String rawToken);
}
