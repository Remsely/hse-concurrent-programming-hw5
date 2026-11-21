package edu.hse.concurrency.links;

import java.io.IOException;
import java.net.URI;

@FunctionalInterface
public interface PageFetcher {

    Page fetch(URI uri) throws IOException, InterruptedException;
}
