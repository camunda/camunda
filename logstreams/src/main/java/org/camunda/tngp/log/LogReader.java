package org.camunda.tngp.log;

import org.camunda.tngp.util.buffer.BufferReader;

/**
 * Utility class for incrementally reading the log in a sequential fashion.
 * Maintains a position which is kept between calls to {@link #read(int)}.
 */
public interface LogReader
{

    void setLogAndPosition(Log log, long position);

    void setPosition(long position);

    boolean read(BufferReader reader);

    long position();

}
