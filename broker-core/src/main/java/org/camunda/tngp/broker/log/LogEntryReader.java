package org.camunda.tngp.broker.log;

import org.camunda.tngp.util.buffer.BufferReader;

public interface LogEntryReader extends BufferReader
{
    int sourceEventPosition();

    int sourceEventLogId();
}
