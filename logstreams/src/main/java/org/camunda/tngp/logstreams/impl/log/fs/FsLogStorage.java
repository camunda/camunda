package org.camunda.tngp.logstreams.impl.log.fs;

import static java.nio.file.StandardCopyOption.*;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;
import static org.camunda.tngp.logstreams.impl.log.fs.FsLogSegment.*;
import static org.camunda.tngp.logstreams.impl.log.fs.FsLogSegmentDescriptor.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.agrona.IoUtil;
import org.agrona.LangUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.util.FileUtil;

public class FsLogStorage implements LogStorage
{
    protected static final int STATE_CREATED = 0;
    protected static final int STATE_OPENED = 1;
    protected static final int STATE_CLOSED = 2;

    protected static final String EXTENSION_SEPARATOR = ".";
    protected static final String SEGMENT_FILE_TRUNCATED_SUFFIX = EXTENSION_SEPARATOR + "truncated";
    protected static final String SEGMENT_FILE_BAK_SUFFIX = EXTENSION_SEPARATOR + "bak";

    protected static final String BACKUP_FILE_NAME_PATTERN = FsLogStorageConfiguration.FRAGMENT_FILE_NAME_PATTERN + SEGMENT_FILE_BAK_SUFFIX;
    protected static final String FRAGMENT_FILE_NAME_PATTERN = BACKUP_FILE_NAME_PATTERN + SEGMENT_FILE_TRUNCATED_SUFFIX;

    protected final FsLogStorageConfiguration config;

    /** Readable log segments */
    protected FsLogSegments logSegments = new FsLogSegments();

    protected FsLogSegment currentSegment;

    protected int dirtySegmentId = -1;

    protected volatile int state = STATE_CREATED;

    public FsLogStorage(final FsLogStorageConfiguration cfg)
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

                markSegmentAsDirty(currentSegment);
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
    public void truncate(long addr)
    {
        ensureOpenedStorage();

        final int currentSegmentId = currentSegment.getSegmentId();
        final int segmentId = partitionId(addr);
        final int segmentOffset = partitionOffset(addr);

        final FsLogSegment segment = logSegments.getSegment(segmentId);
        if (segment == null || segmentOffset < METADATA_LENGTH || segmentOffset >= segment.getSize())
        {
            throw new IllegalArgumentException("Cannot truncate log: invalid address");
        }

        final String sourceFileName = config.fileName(segmentId);
        final String backupFileName = sourceFileName + SEGMENT_FILE_BAK_SUFFIX;
        final String applicableFileName = backupFileName + SEGMENT_FILE_TRUNCATED_SUFFIX;

        final Path source = Paths.get(sourceFileName);
        final Path backup = Paths.get(backupFileName);
        final Path applicable = Paths.get(applicableFileName);

        FileChannel fileChannel = null;
        MappedByteBuffer mappedBuffer = null;
        try
        {
            // copy: segment -> segment.bak
            Files.copy(source, backup, REPLACE_EXISTING);

            fileChannel = FileUtil.openChannel(backupFileName, false);
            fileChannel.truncate(segmentOffset);
            fileChannel.force(true);

            mappedBuffer = fileChannel.map(MapMode.READ_WRITE, 0, METADATA_LENGTH);
            final UnsafeBuffer metadataSection = new UnsafeBuffer(mappedBuffer, 0, METADATA_LENGTH);
            metadataSection.putInt(SEGMENT_SIZE_OFFSET, segmentOffset);
            mappedBuffer.force();

            // move: segment.bak -> segment.bak.truncated
            Files.move(backup, applicable, REPLACE_EXISTING);

            // delete log segments in reverse order
            for (int i = currentSegmentId; segmentId <= i; i--)
            {
                final FsLogSegment segmentToDelete = logSegments.getSegment(i);
                segmentToDelete.closeSegment();
                segmentToDelete.delete();
            }

            // move: segment.bak.truncated -> segment
            Files.move(applicable, source);
        }
        catch (final IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
        finally
        {
            if (fileChannel != null && fileChannel.isOpen())
            {
                try
                {
                    IoUtil.unmap(mappedBuffer);
                    fileChannel.close();
                }
                catch (final IOException e)
                {
                    LangUtil.rethrowUnchecked(e);
                }
            }
        }

        final List<FsLogSegment> segments = new ArrayList<>();
        for (int i = config.initialSegmentId; i < segmentId; i++)
        {
            final FsLogSegment logSegment = new FsLogSegment(config.fileName(i));
            logSegment.openSegment(false);
            logSegment.setFilled();
            segments.add(logSegment);
        }

        currentSegment = new FsLogSegment(config.fileName(segmentId));
        currentSegment.openSegment(false);
        segments.add(currentSegment);

        final FsLogSegments logSegments = new FsLogSegments();
        logSegments.init(config.initialSegmentId, segments.toArray(new FsLogSegment[segments.size()]));

        this.logSegments = logSegments;
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

        deleteBakFilesIfExist(logDir);
        applyTruncatedFileIfExists(logDir);

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

        checkConsistency();

        state = STATE_OPENED;
    }

    protected void checkConsistency()
    {
        try
        {
            if (!currentSegment.isConsistent())
            {
                // try to auto-repair segment
                currentSegment.truncateUncommittedData();
            }

            if (!currentSegment.isConsistent())
            {
                throw new RuntimeException("Inconsistent log segment: " + currentSegment.getFileName());
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            throw new RuntimeException("Fail to check consistency", e);
        }
    }

    protected void deleteBakFilesIfExist(File logDir)
    {
        final List<File> backupFiles = Arrays.asList(logDir.listFiles(this::matchesBackupFragmentFileNamePattern));
        backupFiles.forEach((file) ->
        {
            file.delete();
        });
    }

    protected void applyTruncatedFileIfExists(File logDir)
    {
        final List<File> segments = Arrays.asList(logDir.listFiles(config::matchesFragmentFileNamePattern));
        final List<File> truncatedFiles = Arrays.asList(logDir.listFiles(this::matchesTruncatedFragmentFileNamePattern));

        final int truncatedApplicableFiles = truncatedFiles.size();
        if (truncatedApplicableFiles == 1)
        {
            final File truncatedFile = truncatedFiles.get(0);
            final int truncatedSegmentId = getSegmentId(truncatedFile);

            final int existingSegments = segments.size();

            boolean shouldApply = false;

            if (existingSegments == 0)
            {
                shouldApply = truncatedSegmentId == config.initialSegmentId;
            }
            else if (existingSegments > 0)
            {
                segments.sort((s1, s2) -> Integer.compare(getSegmentId(s1), getSegmentId(s2)));
                final File lastSegment = segments.get(existingSegments - 1);

                final int lastSegmentId = getSegmentId(lastSegment);

                shouldApply = lastSegmentId + 1 == truncatedSegmentId;
            }

            if (shouldApply)
            {
                try
                {
                    final Path truncatedPath = truncatedFile.toPath();
                    final Path initialSegmentPath = Paths.get(config.fileName(truncatedSegmentId));

                    Files.move(truncatedPath, initialSegmentPath);
                }
                catch (IOException e)
                {
                    LangUtil.rethrowUnchecked(e);
                }
            }
            else
            {
                truncatedFiles.forEach((file) ->
                {
                    file.delete();
                });
            }

        }
        else if (truncatedApplicableFiles > 1)
        {
            throw new RuntimeException("Cannot open log storage: multiple truncated files detected");
        }
    }

    @Override
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

        dirtySegmentId = -1;

        state = STATE_CLOSED;
    }

    @Override
    public void flush() throws Exception
    {
        ensureOpenedStorage();

        if (dirtySegmentId >= 0)
        {
            for (int id = dirtySegmentId; id <= currentSegment.getSegmentId(); id++)
            {
                logSegments.getSegment(id).flush();
            }

            dirtySegmentId = -1;
        }
    }

    protected void markSegmentAsDirty(FsLogSegment segment)
    {
        if (dirtySegmentId < 0)
        {
            dirtySegmentId = segment.getSegmentId();
        }
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

    @Override
    public boolean isOpen()
    {
        return state == STATE_OPENED;
    }

    public boolean matchesBackupFragmentFileNamePattern(File file)
    {
        return file.getName().matches(BACKUP_FILE_NAME_PATTERN);
    }

    public boolean matchesTruncatedFragmentFileNamePattern(File file)
    {
        return file.getName().matches(FRAGMENT_FILE_NAME_PATTERN);
    }

    protected int getSegmentId(final File file)
    {
        MappedByteBuffer mappedBuffer = null;
        int segmentId = -1;

        try (final FileChannel fileChannel = FileUtil.openChannel(file.getAbsolutePath(), false))
        {
            mappedBuffer = fileChannel.map(MapMode.READ_WRITE, 0, METADATA_LENGTH);
            final UnsafeBuffer metadataSection = new UnsafeBuffer(mappedBuffer, 0, METADATA_LENGTH);
            segmentId = metadataSection.getInt(SEGMENT_ID_OFFSET);
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }
        finally
        {
            if (mappedBuffer != null)
            {
                IoUtil.unmap(mappedBuffer);
            }
        }
        return segmentId;
    }
}
