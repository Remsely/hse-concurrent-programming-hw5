package edu.hse.concurrency.links;

public final class CheckContext {

    public static final ScopedValue<String> CHECK_ID = ScopedValue.newInstance();

    private CheckContext() {
    }
}
