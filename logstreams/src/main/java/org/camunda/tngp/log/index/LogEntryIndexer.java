package org.camunda.tngp.log.index;

import java.nio.channels.FileChannel;

import org.camunda.tngp.hashindex.HashIndex;

/**
 * Indexes a log entry.
 */
public interface LogEntryIndexer
{
    void indexEntry(
            HashIndex index,
            long position,
            FileChannel fileChannel,
            int offset,
            int length);
}
