package edu.hse.concurrency.links;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

public class LinkCheckProbe {

    private static final System.Logger LOG = System.getLogger(LinkCheckProbe.class.getName());
    private static final URI ROOT = URI.create("https://docs.example/");
    private static final int LINKS = 256;

    public static void main(String[] args) throws InterruptedException {
        List<URI> links = IntStream.range(0, LINKS)
                .mapToObj(i -> URI.create("https://docs" + (i % 4) + ".example/page/" + i))
                .toList();
        PageFetcher fetcher = uri -> {
            LOG.log(System.Logger.Level.DEBUG, "check={0} uri={1}", CheckContext.CHECK_ID.get(), uri);
            Thread.sleep(30);
            return new Page(200, uri.equals(ROOT) ? links : List.of());
        };
        var request = new CheckRequest("benchmark", List.of(ROOT), 1, Duration.ofSeconds(15), 32, 8);
        var virtual = new LinkCheckerImpl(fetcher);
        var platform = new LinkCheckerImpl(fetcher, Thread.ofPlatform().factory());

        run(virtual, request);
        run(platform, request);
        for (int i = 1; i <= 3; i++) {
            double virtualMillis = run(virtual, request);
            double platformMillis = run(platform, request);
            System.out.printf(Locale.ROOT, "run=%d pages=%d virtual=%.1fms platform=%.1fms%n",
                    i, LINKS + 1, virtualMillis, platformMillis);
        }
    }

    private static double run(LinkChecker checker, CheckRequest request) throws InterruptedException {
        long started = System.nanoTime();
        CheckReport report = checker.check(request);
        double elapsedMillis = (System.nanoTime() - started) / 1_000_000.0;
        if (report.timedOut() || report.results().size() != LINKS + 1) {
            throw new IllegalStateException("The probe did not check every page");
        }
        return elapsedMillis;
    }
}
