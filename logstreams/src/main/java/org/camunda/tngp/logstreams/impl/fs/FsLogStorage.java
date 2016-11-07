package org.camunda.tngp.logstreams.impl.fs;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.partitionId;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.partitionOffset;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.position;
import static org.camunda.tngp.logstreams.impl.fs.FsLogSegment.END_OF_SEGMENT;
import static org.camunda.tngp.logstreams.impl.fs.FsLogSegment.NO_DATA;
import static org.camunda.tngp.logstreams.impl.fs.FsLogSegmentDescriptor.METADATA_LENGTH;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.util.FileUtil;

public class FsLogStorage implements LogStorage
{
    protected static final int STATE_CREATED = 0;
    protected static final int STATE_OPENED = 1;
    protected static final int STATE_CLOSED = 2;

    protected final FsLogStorageConfiguration config;

    /** Readable log segments */
    protected final FsLogSegments logSegments = new FsLogSegments();

    protected FsLogSegment currentSegment;

    protected volatile int state = STATE_CREATED;

    public FsLogStorage(FsLogStorageConfiguration cfg)
    {
        this.config = cfg;
    }

    @Override
    public boolean isByteAddressable()
    {
        return true;
    }

    @Override
    public long append(ByteBuffer buffer)
    {
        ensureOpenedStorage();

        final int size = currentSegment.getSize();
        final int capacity = currentSegment.getCapacity();
        final int remainingCapacity = capacity - size;
        final int requiredCapacity = buffer.remaining();

        if (requiredCapacity > config.getSegmentSize())
        {
            return OP_RESULT_BLOCK_SIZE_TOO_BIG;
        }

        if (remainingCapacity < requiredCapacity)
        {
            onSegmentFilled();
        }

        long opresult = -1;

        if (currentSegment != null)
        {
            final int appendResult = currentSegment.append(buffer);

            if (appendResult >= 0)
            {
                opresult = position(currentSegment.getSegmentId(), appendResult);
            }
        }

        return opresult;
    }

    protected void onSegmentFilled()
    {
        final FsLogSegment filledSegment = currentSegment;

        final int nextSegmentId = 1 + filledSegment.getSegmentId();
        final String nextSegmentName = config.fileName(nextSegmentId);
        final FsLogSegment newSegment = new FsLogSegment(nextSegmentName);

        if (newSegment.allocate(nextSegmentId, config.getSegmentSize()))
        {
            logSegments.addSegment(newSegment);
            currentSegment = newSegment;
            // Do this last so readers do not attempt to advance to next segment yet
            // before it is visible
            filledSegment.setFilled();
        }
    }

    @Override
    public long read(ByteBuffer readBuffer, long addr)
    {
        ensureOpenedStorage();

        final int segmentId = partitionId(addr);
        final int segementOffset = partitionOffset(addr);

        final FsLogSegment segment = logSegments.getSegment(segmentId);

        long opStatus = OP_RESULT_INVALID_ADDR;

        if (segment != null)
        {
            final int readResult = segment.readBytes(readBuffer, segementOffset);

            if (readResult >= 0)
            {
                opStatus = position(segmentId, segementOffset + readResult);
            }
            else if (readResult == END_OF_SEGMENT)
            {
                final long nextAddr = position(segmentId + 1, METADATA_LENGTH);
                // move to next segment
                return read(readBuffer, nextAddr);
            }
            else if (readResult == NO_DATA)
            {
                opStatus = OP_RESULT_NO_DATA;
            }
        }

        return opStatus;
    }

    @Override
    public void open()
    {
        ensureNotOpenedStorage();

        final String path = config.getPath();
        final List<FsLogSegment> readableLogSegments = new ArrayList<>();
        final File logDir = new File(path);
        final List<File> logFiles = Arrays.asList(logDir.listFiles(config::matchesFragmentFileNamePattern));

        logFiles.forEach((file) ->
        {
            final FsLogSegment segment = new FsLogSegment(file.getAbsolutePath());
            if (segment.openSegment(false))
            {
                readableLogSegments.add(segment);
            }
            else
            {
                throw new RuntimeException("Cannot open log segment " + file);
            }

        });

        // sort segments by id
        readableLogSegments.sort((s1, s2) -> Integer.compare(s1.getSegmentId(), s2.getSegmentId()));

        // set all segments but the last one filled
        for (int i = 0; i < readableLogSegments.size() - 1; i++)
        {
            readableLogSegments.get(i).setFilled();
        }

        final int existingSegments = readableLogSegments.size();

        if (existingSegments > 0)
        {
            currentSegment = readableLogSegments.get(existingSegments - 1);
        }
        else
        {
            final int initialSegmentId = config.initialSegmentId;
            final String initialSegmentName = config.fileName(initialSegmentId);
            final int segmentSize = config.getSegmentSize();

            final FsLogSegment initialSegment = new FsLogSegment(initialSegmentName);

            if (!initialSegment.allocate(initialSegmentId, segmentSize))
            {

                throw new RuntimeException("Cannot allocate initial segment");
            }

            currentSegment = initialSegment;
            readableLogSegments.add(initialSegment);
        }

        final FsLogSegment[] segmentsArray = readableLogSegments.toArray(new FsLogSegment[readableLogSegments.size()]);
        logSegments.init(config.initialSegmentId, segmentsArray);

        state = STATE_OPENED;
    }

    public void close()
    {
        ensureOpenedStorage();

        logSegments.closeAll();

        if (config.isDeleteOnClose())
        {
            final String logPath = config.getPath();
            try
            {
                FileUtil.deleteFolder(logPath);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        state = STATE_CLOSED;
    }

    public FsLogStorageConfiguration getConfig()
    {
        return config;
    }

    @Override
    public long getFirstBlockAddress()
    {
        ensureOpenedStorage();

        final FsLogSegment firstSegment = logSegments.getFirst();
        if (firstSegment != null && firstSegment.getSizeVolatile() > METADATA_LENGTH)
        {
            return position(firstSegment.getSegmentId(), METADATA_LENGTH);
        }
        else
        {
            return -1;
        }
    }

    protected void ensureOpenedStorage()
    {
        if (state == STATE_CREATED)
        {
            throw new IllegalStateException("log storage is not open");
        }
        if (state == STATE_CLOSED)
        {
            throw new IllegalStateException("log storage is already closed");
        }
    }

    protected void ensureNotOpenedStorage()
    {
        if (state == STATE_OPENED)
        {
            throw new IllegalStateException("log storage is already opened");
        }
    }

}
