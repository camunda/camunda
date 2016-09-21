package org.camunda.tngp.log;

import org.agrona.DirectBuffer;
import org.camunda.tngp.util.buffer.BufferReader;

public interface ReadableLogEntry
{
    long getPosition();

    long getLongKey();

    DirectBuffer getValueBuffer();

    int getValueOffset();

    int getValueLength();

    void readValue(BufferReader reader);
}
