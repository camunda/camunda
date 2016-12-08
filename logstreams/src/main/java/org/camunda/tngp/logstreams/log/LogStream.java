package org.camunda.tngp.logstreams.log;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a stream of events.
 */
public interface LogStream extends AutoCloseable
{
    /**
     * @return the log stream's id
     */
    int getId();

    /**
     * Opens the log stream synchronously. This blocks until the log stream is
     * opened.
     */
    void open();

    /**
     * Opens the log stream asynchronously.
     */
    CompletableFuture<Void> openAsync();

    /**
     * Closes the log stream synchronously. This blocks until the log stream is
     * closed.
     */
    void close();

    /**
     * Closes the log stream asynchronous.
     */
    CompletableFuture<Void> closeAsync();

    /**
     * @return the current position of the log appender, or a negative value if
     *         the log stream is not open
     */
    long getCurrentAppenderPosition();

    /**
     * Register a failure listener.
     */
    void registerFailureListener(LogStreamFailureListener listener);

    /**
     * Remove a registered failure listener.
     */
    void removeFailureListener(LogStreamFailureListener listener);

    StreamContext getContext();

}
