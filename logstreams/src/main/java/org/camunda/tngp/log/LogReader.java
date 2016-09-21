package org.camunda.tngp.log;

import java.util.Iterator;

public interface LogReader extends Iterator<ReadableLogEntry>
{
    void wrap(Log log);

    void wrap(Log log, long position);

    void seek(long seekPosition);

    void seekToFirstEntry();

    void seekToLastEntry();

    long getPosition();
}