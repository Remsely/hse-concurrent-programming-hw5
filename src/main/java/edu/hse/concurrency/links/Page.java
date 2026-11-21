package edu.hse.concurrency.links;

import java.net.URI;
import java.util.List;

public record Page(int statusCode, List<URI> links) {

    public Page {
        links = List.copyOf(links);
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("Invalid HTTP status: " + statusCode);
        }
    }
}
