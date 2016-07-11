package org.camunda.tngp.broker.log;

import org.camunda.tngp.util.buffer.BufferReader;

public interface LogEntryHandler<T extends BufferReader>
{
    void handle(long position, T reader);
}
