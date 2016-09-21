package org.camunda.tngp.log;

import org.agrona.DirectBuffer;

public interface ReadableFragment
{
    int getStreamId();

    int getType();

    int getVersion();

    int getMessageOffset();

    int getMessageLength();

    DirectBuffer getBuffer();
}
