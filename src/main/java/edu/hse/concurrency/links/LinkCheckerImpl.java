package edu.hse.concurrency.links;

import java.util.concurrent.ThreadFactory;

public class LinkCheckerImpl implements LinkChecker {

    public LinkCheckerImpl(PageFetcher fetcher) {
        this(fetcher, Thread.ofVirtual().factory());
    }

    public LinkCheckerImpl(PageFetcher fetcher, ThreadFactory threadFactory) {
        // TODO
    }

    @Override
    public CheckReport check(CheckRequest request) throws InterruptedException {
        throw new UnsupportedOperationException("TODO");
    }
}
