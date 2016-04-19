package org.camunda.tngp.log;

/**
 * Utitilty class for incrementally reading the log in a sequential fashion.
 * Maintains a position which is kept between calls to {@link #read(int)}.
 */
public class LogReader
{
    protected Log log;
    protected LogFragmentHandler fragmentHandler;

    /**
     * The current position of the reader
     */
    protected long position;

    public LogReader(Log log, LogFragmentHandler fragmentHandler)
    {
        setLog(log);
        setFragmentHandler(fragmentHandler);
    }

    public LogReader()
    {
    }

    public void setLog(Log log)
    {
        this.log = log;
        this.position = log.getInitialPosition();
    }

    public void setFragmentHandler(LogFragmentHandler fragmentHandler)
    {
        this.fragmentHandler = fragmentHandler;
    }

    public void setPosition(long position)
    {
        this.position = position;
    }

    public int read(int maxRecords)
    {
        if(maxRecords < 0)
        {
            throw new IllegalArgumentException("Cannot read from log: maxRecords needs to be a positive number.");
        }

        int recordRead = 0;

        while(recordRead < maxRecords)
        {
            long nextPosition = log.pollFragment(position, fragmentHandler);

            if(nextPosition == -1)
            {
                break;
            }
            else
            {
                position = nextPosition;
                ++recordRead;
            }
        }

        return recordRead;
    }

    public long getPosition()
    {
        return position;
    }

}
