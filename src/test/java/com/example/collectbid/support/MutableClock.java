package com.example.collectbid.support;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {
    private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    public void set(Instant instant) { time.set(instant); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
    @Override public Instant instant() { return time.get(); }
}
