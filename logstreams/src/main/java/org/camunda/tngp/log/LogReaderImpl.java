package org.camunda.tngp.log;

import org.camunda.tngp.util.buffer.BufferReader;

/**
 * Utility class for incrementally reading the log in a sequential fashion.
 * Maintains a position which is kept between calls to {@link #read(int)}.
 */
public class LogReaderImpl implements LogReader
{
    public static final int DEFAULT_SIZE = 1024 * 1024;

    protected Log log;

    /**
     * The current position of the reader
     */
    protected long position;

    protected final LogEntryReader entryReader;

    public LogReaderImpl(final Log log)
    {
        this(log, DEFAULT_SIZE);
    }

    public LogReaderImpl(final int readBufferSize)
    {
        this.entryReader = new LogEntryReader(readBufferSize);
    }

    public LogReaderImpl(final Log log, final int readBufferSize)
    {
        this(readBufferSize);
        this.log = log;
        this.position = log.getInitialPosition();
    }

    public void setLogAndPosition(Log log, long position)
    {
        this.log = log;
        this.position = position;
    }

    public void setPosition(long position)
    {
        this.position = position;
    }

    public boolean read(BufferReader reader)
    {
        final long nextPosition = entryReader.read(log, position, reader);

        boolean hasNext = false;

        if (nextPosition != -1)
        {
            this.position = nextPosition;
            hasNext = true;
        }

        return hasNext;
    }

    public long position()
    {
        return position;
    }

}
