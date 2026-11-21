package edu.hse.concurrency.links;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record CheckRequest(String checkId, List<URI> startUris, int maxDepth, Duration timeout,
                           int maxConcurrentRequests, int maxRequestsPerHost) {

    public CheckRequest {
        Objects.requireNonNull(checkId, "checkId");
        Objects.requireNonNull(timeout, "timeout");
        startUris = List.copyOf(startUris).stream().map(PageUris::canonical).distinct().toList();
        if (checkId.isBlank()) {
            throw new IllegalArgumentException("checkId must not be blank");
        }
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be nonnegative");
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("timeout must be positive and at most one hour");
        }
        if (maxConcurrentRequests < 1 || maxRequestsPerHost < 1) {
            throw new IllegalArgumentException("Request limits must be positive");
        }
    }
}
