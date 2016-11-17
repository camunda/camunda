package org.camunda.tngp.logstreams;

import org.agrona.DirectBuffer;
import org.camunda.tngp.util.buffer.BufferReader;

/**
 * Represents an event on the log stream.
 */
public interface LoggedEvent
{
    /**
     * @return the event's position in the log.
     */
    long getPosition();

    /**
     * @return the key of the event
     */
    long getLongKey();

    /**
     * @return the buffer which contains the value of the event
     */
    DirectBuffer getValueBuffer();

    /**
     * @return the buffer offset where the event's value can read from
     */
    int getValueOffset();

    /**
     * @return the length of the event's value
     */
    int getValueLength();

    /**
     * Wraps the given buffer to read the event's value.
     *
     * @param reader
     *            the buffer to read from
     */
    void readValue(BufferReader reader);
}
