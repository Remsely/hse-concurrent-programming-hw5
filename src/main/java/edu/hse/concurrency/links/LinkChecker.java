package edu.hse.concurrency.links;

public interface LinkChecker {

    CheckReport check(CheckRequest request) throws InterruptedException;
}
