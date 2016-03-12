package org.camunda.tngp.dispatcher.impl.allocation;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;

/**
 * Allocates a buffer in a mapped file.
 */
public class MappedFileAllocator implements BufferAllocator<MappedFileAllocationDescriptor>
{

    @Override
    public AllocatedBuffer allocate(MappedFileAllocationDescriptor descriptor)
    {
        final File file = descriptor.getFile();
        final long logCapacity = descriptor.getCapacity();
        long startProsition = descriptor.getStartPosition();

        RandomAccessFile raf = null;

        try
        {
            raf = new RandomAccessFile(file, "rw");

            final MappedByteBuffer mappedBuffer = raf.getChannel().map(MapMode.READ_WRITE, startProsition, logCapacity);

            return new AllocatedMappedFile(mappedBuffer, raf);
        }
        catch (Exception e)
        {
            if(raf != null)
            {
                try
                {
                    raf.close();
                }
                catch (IOException e1)
                {
                    // ignore silently
                }
            }

            throw new RuntimeException("Could not map file "+file+" into memory: "+e.getMessage(), e);
        }

    }

}
