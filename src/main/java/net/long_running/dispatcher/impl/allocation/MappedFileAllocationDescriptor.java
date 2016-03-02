package net.long_running.dispatcher.impl.allocation;

import java.io.File;

public class MappedFileAllocationDescriptor extends AllocationDescriptor
{

    protected File file;

    protected long startPosition;

    public MappedFileAllocationDescriptor(int capacity, File file)
    {
        super(capacity);
        this.file = file;
    }

    public File getFile()
    {
        return file;
    }

    public void setFile(File file)
    {
        this.file = file;
    }

    public long getStartPosition()
    {
        return startPosition;
    }

    public void setStartPosition(long startProsition)
    {
        this.startPosition = startProsition;
    }
}
