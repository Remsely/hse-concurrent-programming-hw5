package edu.hse.concurrency.links;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class PageUris {

    private PageUris() {
    }

    public static URI canonical(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getPort() == 0 || uri.getPort() > 65_535) {
            throw new IllegalArgumentException("Expected an HTTP(S) URI with a host: " + uri);
        }

        scheme = scheme.toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        String authority = port < 0 || defaultPort ? host : host + ":" + port;
        String path = uri.normalize().getRawPath();
        String query = uri.getRawQuery();
        return URI.create(scheme + "://" + authority + (path.isEmpty() ? "/" : path)
                + (query == null ? "" : "?" + query));
    }

    public static Optional<URI> follow(URI page, URI link) {
        try {
            String reference = link.toString();
            if (reference.isEmpty()) {
                return Optional.of(canonical(page));
            }
            if (reference.startsWith("?")) {
                String base = canonical(page).toString();
                int queryStart = base.indexOf('?');
                String address = queryStart < 0 ? base : base.substring(0, queryStart);
                return Optional.of(canonical(URI.create(address + reference)));
            }
            return Optional.of(canonical(page.resolve(link)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
