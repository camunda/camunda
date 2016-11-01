package org.camunda.tngp.logstreams;

import org.agrona.DirectBuffer;
import org.camunda.tngp.util.buffer.BufferReader;

public interface LoggedEvent
{
    /**
     * @return this event's position in the stream.
     */
    long getPosition();

    long getLongKey();

    DirectBuffer getValueBuffer();

    int getValueOffset();

    int getValueLength();

    void readValue(BufferReader reader);
}
