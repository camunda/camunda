package org.camunda.tngp.dispatcher.impl.allocation;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class AllocatedMappedFile extends AllocatedBuffer
{

    protected final RandomAccessFile raf;

    public AllocatedMappedFile(ByteBuffer buffer, RandomAccessFile raf)
    {
        super(buffer);
        this.raf = raf;
    }

    @Override
    public void close() throws IOException
    {
        raf.close();
    }

    public RandomAccessFile getFile()
    {
        return raf;
    }

}
