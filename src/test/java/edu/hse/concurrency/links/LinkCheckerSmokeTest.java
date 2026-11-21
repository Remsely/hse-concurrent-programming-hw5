package edu.hse.concurrency.links;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.hse.concurrency.links.LinkResult.Status.HTTP_ERROR;
import static edu.hse.concurrency.links.LinkResult.Status.IO_ERROR;
import static edu.hse.concurrency.links.LinkResult.Status.OK;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class LinkCheckerSmokeTest {

    private static final URI START = URI.create("https://docs.example/");
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    @Test
    void findsShortestDepthAndChecksCanonicalUriOnce() throws Exception {
        URI intro = START.resolve("/guide/intro");
        URI revised = START.resolve("/guide/intro?rev=2");
        URI api = START.resolve("/api");
        URI shared = START.resolve("/shared");
        var graph = Map.of(
                START, page("/guide/intro", "/api#top", "https://DOCS.example:443/api#details", "mailto:a@example.org"),
                intro, page("../api", "../shared", "?rev=2#changes"),
                api, page("/shared", "/"),
                revised, page(),
                shared, page("/outside-depth")
        );
        var calls = new ConcurrentHashMap<URI, AtomicInteger>();
        LinkChecker checker = new LinkCheckerImpl(uri -> {
            calls.computeIfAbsent(uri, ignored -> new AtomicInteger()).incrementAndGet();
            assertTrue(Thread.currentThread().isVirtual());
            assertEquals("docs-1", CheckContext.CHECK_ID.get());
            return graph.get(uri);
        });

        CheckReport report = checker.check(new CheckRequest("docs-1", List.of(START), 2, TIMEOUT, 4, 2));

        assertFalse(report.timedOut());
        assertEquals(Map.of(
                START, new LinkResult(0, OK, 200),
                intro, new LinkResult(1, OK, 200),
                api, new LinkResult(1, OK, 200),
                revised, new LinkResult(2, OK, 200),
                shared, new LinkResult(2, OK, 200)
        ), report.results());
        assertEquals(graph.keySet(), calls.keySet());
        calls.values().forEach(count -> assertEquals(1, count.get()));
    }

    @Test
    void aBadPageDoesNotStopTheOtherChecks() throws Exception {
        LinkChecker checker = new LinkCheckerImpl(uri -> switch (uri.getPath()) {
            case "/" -> page("/missing", "/unreachable", "/ok");
            case "/missing" -> new Page(404, List.of(URI.create("/ignore")));
            case "/unreachable" -> throw new IOException("connection refused");
            case "/ok" -> page();
            default -> throw new AssertionError(uri);
        });

        CheckReport report = checker.check(new CheckRequest("errors", List.of(START), 2, TIMEOUT, 4, 4));

        assertFalse(report.timedOut());
        assertEquals(Map.of(
                START, new LinkResult(0, OK, 200),
                START.resolve("/missing"), new LinkResult(1, HTTP_ERROR, 404),
                START.resolve("/unreachable"), new LinkResult(1, IO_ERROR, null),
                START.resolve("/ok"), new LinkResult(1, OK, 200)
        ), report.results());
    }

    @Test
    void appliesBothLimitsWithoutBlockingAnotherHost() throws Exception {
        var allActive = new AtomicInteger();
        var hostActive = new ConcurrentHashMap<String, AtomicInteger>();
        var peak = new AtomicInteger();
        var hostPeak = new AtomicInteger();
        Set<String> started = ConcurrentHashMap.newKeySet();
        var bothHosts = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        LinkChecker checker = new LinkCheckerImpl(uri -> {
            var sameHost = hostActive.computeIfAbsent(uri.getHost(), ignored -> new AtomicInteger());
            peak.accumulateAndGet(allActive.incrementAndGet(), Math::max);
            hostPeak.accumulateAndGet(sameHost.incrementAndGet(), Math::max);
            if (started.add(uri.getHost())) {
                bothHosts.countDown();
            }
            try {
                release.await();
                return page();
            } finally {
                sameHost.decrementAndGet();
                allActive.decrementAndGet();
            }
        });
        List<URI> seeds = List.of(
                START.resolve("/a"),
                URI.create("http://docs.example:8080/b"),
                URI.create("https://api.example/c"),
                URI.create("https://help.example/d")
        );

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var run = executor.submit(() -> checker.check(new CheckRequest("limits", seeds, 0, TIMEOUT, 2, 1)));
            try {
                assertTrue(bothHosts.await(2, SECONDS), "Two different hosts should check links concurrently");
            } finally {
                release.countDown();
            }
            CheckReport report = run.get(2, SECONDS);
            assertEquals(4, report.results().size());
            assertEquals(2, peak.get());
            assertEquals(1, hostPeak.get());
        }
    }

    @Test
    void deadlineReturnsPartialResultsAfterInterruptingSlowRequest() throws Exception {
        var interrupted = new AtomicBoolean();
        var release = new CountDownLatch(1);
        URI slow = START.resolve("/slow");
        LinkChecker checker = new LinkCheckerImpl(uri -> {
            if (uri.equals(slow)) {
                try {
                    release.await(4, SECONDS);
                } catch (InterruptedException error) {
                    interrupted.set(true);
                    throw error;
                }
            }
            return page();
        });

        try {
            CheckReport report = checker.check(
                    new CheckRequest("deadline", List.of(START, slow), 0, Duration.ofSeconds(1), 2, 2));

            assertTrue(report.timedOut());
            assertEquals(Map.of(START, new LinkResult(0, OK, 200)), report.results());
            assertTrue(interrupted.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void interruptingCheckStopsItsRequests() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        LinkChecker checker = new LinkCheckerImpl(uri -> {
            started.countDown();
            try {
                release.await();
                return page();
            } finally {
                finished.countDown();
            }
        });
        var outcome = new CompletableFuture<Throwable>();
        Thread owner = Thread.ofVirtual().start(() -> {
            try {
                checker.check(new CheckRequest("interrupt", List.of(START), 0, TIMEOUT, 1, 1));
                outcome.complete(null);
            } catch (Throwable error) {
                outcome.complete(error);
            }
        });

        try {
            assertTrue(started.await(2, SECONDS));
            owner.interrupt();
            assertInstanceOf(InterruptedException.class, outcome.get(2, SECONDS));
            assertEquals(0, finished.getCount());
        } finally {
            release.countDown();
            owner.join(Duration.ofSeconds(2));
        }
    }

    private static Page page(String... links) {
        return new Page(200, Arrays.stream(links).map(URI::create).toList());
    }
}
