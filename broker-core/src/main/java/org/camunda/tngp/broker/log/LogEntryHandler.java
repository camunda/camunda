package org.camunda.tngp.broker.log;

import org.camunda.tngp.util.buffer.BufferReader;

public interface LogEntryHandler<T extends BufferReader>
{
    int CONSUME_ENTRY_RESULT = 0;
    int POSTPONE_ENTRY_RESULT = 1;
    int FAILED_ENTRY_RESULT = 2;

    int handle(long position, T reader);
}
