package org.camunda.tngp.log.fs;

import static org.camunda.tngp.log.fs.LogSegmentDescriptor.METADATA_LENGTH;
import static org.camunda.tngp.log.fs.LogSegmentDescriptor.SEGMENT_ID_OFFSET;
import static org.camunda.tngp.log.fs.LogSegmentDescriptor.SEGMENT_SIZE_OFFSET;
import static org.camunda.tngp.log.fs.LogSegmentDescriptor.SEGMENT_TAIL_OFFSET;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import org.camunda.tngp.log.util.FileChannelUtil;

import uk.co.real_logic.agrona.IoUtil;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public abstract class LogSegment
{
    protected final String fileName;

    protected FileChannel fileChannel;

    protected UnsafeBuffer metadataSection;

    protected MappedByteBuffer mappedBuffer;

    public LogSegment(String fileName)
    {
        this.fileName = fileName;
    }

    public boolean openSegment(boolean create)
    {
        fileChannel = FileChannelUtil.openChannel(fileName, create);

        if (fileChannel != null)
        {
            try
            {
                mappedBuffer = fileChannel.map(MapMode.READ_WRITE, 0, METADATA_LENGTH);
                metadataSection = new UnsafeBuffer(mappedBuffer, 0, METADATA_LENGTH);
            }
            catch (IOException e)
            {
                fileChannel = null;
                metadataSection = null;
                LangUtil.rethrowUnchecked(e);
            }
        }

        return fileChannel != null;
    }

    public void closeSegment()
    {
        try
        {
            this.metadataSection = null;
            IoUtil.unmap(mappedBuffer);
            fileChannel.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void delete()
    {
        final File file = new File(fileName);
        if (file.exists())
        {
            file.delete();
        }
    }

    public int getSegmentId()
    {
        return metadataSection.getInt(SEGMENT_ID_OFFSET);
    }

    protected void setSegmentId(int segmentId)
    {
        metadataSection.putInt(SEGMENT_ID_OFFSET, segmentId);
    }

    public int getTail()
    {
        return metadataSection.getInt(SEGMENT_TAIL_OFFSET);
    }

    public int getTailVolatile()
    {
        return metadataSection.getIntVolatile(SEGMENT_TAIL_OFFSET);
    }

    protected void setTailOrdered(int tail)
    {
        metadataSection.putIntOrdered(SEGMENT_TAIL_OFFSET, tail);
    }

    protected void setTailVolatile(int tail)
    {
        metadataSection.putIntVolatile(SEGMENT_TAIL_OFFSET, tail);
    }

    public int getSegmentSize()
    {
        return metadataSection.getInt(SEGMENT_SIZE_OFFSET);
    }

    protected void setSegmentSize(int size)
    {
        metadataSection.putInt(SEGMENT_SIZE_OFFSET, size);
    }

    protected boolean isFilled()
    {
        return getSegmentSize() == getTailVolatile();
    }

    public String getFileName()
    {
        return fileName;
    }

}
