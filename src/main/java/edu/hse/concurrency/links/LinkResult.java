package edu.hse.concurrency.links;

public record LinkResult(int depth, Status status, Integer statusCode) {

    public enum Status {
        OK,
        HTTP_ERROR,
        IO_ERROR
    }
}
