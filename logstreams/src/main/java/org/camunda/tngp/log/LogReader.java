package org.camunda.tngp.log;

/**
 * Utitilty class for incrementally reading the log in a sequential fashion.
 * Maintains a position which is kept between calls to {@link #read(int)}.
 */
public class LogReader
{
    protected Log log;

    /**
     * The current position of the reader
     */
    protected long position;

    protected final LogEntryReader entryReader;

    public LogReader(final Log log, final int readBufferSize)
    {
        this.log = log;
        this.position = log.getInitialPosition();
        entryReader = new LogEntryReader(readBufferSize);
    }

    public void setPosition(long position)
    {
        this.position = position;
    }

    public boolean read(FragmentReader reader)
    {
       final long nextPosition = entryReader.read(log, position, reader);

       boolean hasNext = false;

       if(nextPosition != -1)
       {
           this.position = nextPosition;
           hasNext = true;
       }

       return hasNext;
    }

    public long getPosition()
    {
        return position;
    }

}
