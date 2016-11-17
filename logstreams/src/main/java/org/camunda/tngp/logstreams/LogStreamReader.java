package org.camunda.tngp.logstreams;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Reads the log stream in an iterator-like pattern. Common usage:
 *
 * <pre>
 * <code>
 * reader.wrap(log);
 *
 * // optionally seek to a position
 * reader.seek(position);
 *
 * while(reader.hasNext())
 * {
 *     final LoggedEvent event = reader.next();
 *     // process the event..
 * }
 * </code>
 * </pre>
 *
 */
public interface LogStreamReader extends Iterator<LoggedEvent>
{
    /**
     * Initialize the reader and seek to the first event.
     *
     * @param log
     *            the stream which provides the log
     */
    void wrap(LogStream log);

    /**
     * Initialize the reader and seek to the given log position.
     *
     * @param log
     *            the stream which provides the log
     * @param position
     *            the position in the log to seek to
     */
    void wrap(LogStream log, long position);

    /**
     * Seek to the given log position if exists.
     *
     * @param position
     *            the position in the log to seek to
     */
    void seek(long position);

    /**
     * Seek to the log position of the first event.
     */
    void seekToFirstEvent();

    /**
     * Seek to the log position of the last event.
     */
    void seekToLastEvent();

    /**
     * Returns the current log position of the reader.
     *
     * @return the current log position
     *
     * @throws NoSuchElementException
     *             if the log is empty
     * @throws IllegalStateException
     *             if the log is not initialized
     */
    long getPosition();
}