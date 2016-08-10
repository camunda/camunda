package org.camunda.tngp.broker.log.idx;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.services.HashIndexManager;

public interface IndexWriter
{

    void indexLogEntry(long position, LogEntryHeaderReader reader);

    // oder analog gibt der nur die Positionen raus
    HashIndexManager<?> getIndexManager();
}
