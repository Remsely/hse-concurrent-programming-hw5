package edu.hse.concurrency.links;

import java.net.URI;
import java.util.Map;

public record CheckReport(boolean timedOut, Map<URI, LinkResult> results) {

    public CheckReport {
        results = Map.copyOf(results);
    }
}
