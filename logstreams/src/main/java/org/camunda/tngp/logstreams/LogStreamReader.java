package org.camunda.tngp.logstreams;

import java.util.Iterator;

public interface LogStreamReader extends Iterator<LoggedEvent>
{
    void wrap(LogStream log);

    void wrap(LogStream log, long position);

    void seek(long seekPosition);

    void seekToFirstEvent();

    void seekToLastEvent();

    long getPosition();
}