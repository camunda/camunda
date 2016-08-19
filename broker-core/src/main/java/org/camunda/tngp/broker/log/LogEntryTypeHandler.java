package org.camunda.tngp.broker.log;

import org.camunda.tngp.util.buffer.BufferReader;

public interface LogEntryTypeHandler<T extends BufferReader>
{

    void handle(T reader, ResponseControl responseControl, LogWriters logWriters);

}
