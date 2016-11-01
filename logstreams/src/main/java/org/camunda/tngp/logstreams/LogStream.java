package org.camunda.tngp.logstreams;

import java.util.concurrent.CompletableFuture;

public interface LogStream extends AutoCloseable
{
    int getId();

    long getInitialPosition();

    void open();

    CompletableFuture<Void> openAsync();

    void close();

    CompletableFuture<Void> closeAsync();

    void registerFailureListener(LogStreamFailureListener listener);

    void removeFailureListener(LogStreamFailureListener listener);
}
