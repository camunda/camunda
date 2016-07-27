package org.camunda.tngp.broker.idx;

import org.camunda.tngp.util.buffer.BufferReader;

public interface LogEntryTracker<T extends BufferReader>
{

    void onLogEntryStaged(T logEntry);

    void onLogEntryFailed(T logEntry);

    void onLogEntryCommit(T logEntry, long position);
}
