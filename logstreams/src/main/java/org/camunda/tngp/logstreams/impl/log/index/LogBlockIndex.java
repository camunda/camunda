package org.camunda.tngp.logstreams.impl.log.index;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.camunda.tngp.logstreams.impl.log.index.LogBlockIndexDescriptor.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.function.Function;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.log.LoggedEventImpl;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.util.StreamUtil;

/**
 * Block index, mapping an event's position to the physical address of the block in which it resides
 * in storage.
 *<p>
 * Each Event has a position inside the stream. This position addresses it uniquely and is assigned
 * when the entry is first published to the stream. The position never changes and is preserved
 * through maintenance operations like compaction.
 *<p>
 * In order to read an event, the position must be translated into the "physical address" of the
 * block in which it resides in storage. Then, the block can be scanned for the event position requested.
 *
 */
public class LogBlockIndex implements SnapshotSupport
{
    protected final AtomicBuffer indexBuffer;

    protected final int capacity;

    protected long lastVirtualPosition = -1;

    public LogBlockIndex(int capacity, Function<Integer, AtomicBuffer> bufferAllocator)
    {
        final int requiredBufferCapacity = dataOffset() + (capacity * entryLength());

        this.indexBuffer = bufferAllocator.apply(requiredBufferCapacity);
        this.capacity = capacity;

        reset();
    }

    /**
     * Returns the physical address of the block in which the log entry identified by the provided position
     * resides.
     *
     * @param position a virtual log position
     * @return the physical address of the block containing the log entry identified by the provided
     * virtual position
     */
    public long lookupBlockAddress(long position)
    {
        final int offset = lookupOffset(position);
        return offset >= 0 ? indexBuffer.getLong(entryAddressOffset(offset)) : offset;
    }

    /**
     * Returns the position of the first log entry of the the block in which the log entry identified
     * by the provided position resides.
     *
     * @param position a virtual log position
     * @return the position of the block containing the log entry identified by the provided
     * virtual position
     */
    public long lookupBlockPosition(long position)
    {
        final int offset = lookupOffset(position);
        return offset >= 0 ? indexBuffer.getLong(entryLogPositionOffset(offset)) : offset;
    }

    /**
     * Returns the offset of the block in which the log entry identified by the provided position
     * resides.
     *
     * @param position a virtual log position
     * @return the offset of the block containing the log entry identified by the provided
     * virtual position
     */
    protected int lookupOffset(long position)
    {
        final int idx = lookupIndex(position);
        return idx >= 0 ? entryOffset(idx) : idx;
    }

    /**
     * Returns the index of the block in which the log entry identified by the provided position
     * resides.
     *
     * @param position a virtual log position
     * @return the index of the block containing the log entry identified by the provided
     * virtual position
     */
    protected int lookupIndex(long position)
    {
        final int lastEntryIdx = size() - 1;

        int low = 0;
        int high = lastEntryIdx;

        int idx = -1;

        if (low == high)
        {
            final int entryOffset = entryOffset(low);
            final long entryValue = indexBuffer.getLong(entryLogPositionOffset(entryOffset));

            if (entryValue <= position)
            {
                idx = low;
            }

            high = -1;
        }

        while (low <= high)
        {
            final int mid = (low + high) >>> 1;
            final int entryOffset = entryOffset(mid);

            if (mid == lastEntryIdx)
            {
                idx = mid;
                break;
            }
            else
            {
                final long entryValue = indexBuffer.getLong(entryLogPositionOffset(entryOffset));
                final long nextEntryValue = indexBuffer.getLong(entryLogPositionOffset(entryOffset(mid + 1)));

                if (entryValue <= position && position < nextEntryValue)
                {
                    idx = mid;
                    break;
                }
                else if (entryValue < position)
                {
                    low = mid + 1;
                }
                else if (entryValue > position)
                {
                    high = mid - 1;
                }
            }
        }

        return idx;
    }

    /**
     * Invoked by the log Appender thread after it has first written one or more entries
     * to a block.
     *
     * @param logPosition the virtual position of the block (equal or smaller to the v position of the first entry in the block)
     * @param storageAddr the physical address of the block in the underlying storage
     * @return the new size of the index.
     */
    public int addBlock(long logPosition, long storageAddr)
    {
        final int currentIndexSize = indexBuffer.getInt(indexSizeOffset()); // volatile get not necessary
        final int entryOffset = entryOffset(currentIndexSize);
        final int newIndexSize = 1 + currentIndexSize;

        if (newIndexSize > capacity)
        {
            throw new RuntimeException(String.format("LogBlockIndex capacity of %d entries reached. Cannot add new block.", capacity));
        }

        if (lastVirtualPosition >= logPosition)
        {
            final String errorMessage = String.format("Illegal value for position.Value=%d, last value in index=%d. Must provide positions in ascending order.", logPosition, lastVirtualPosition);
            throw new IllegalArgumentException(errorMessage);
        }

        lastVirtualPosition = logPosition;

        // write next entry
        indexBuffer.putLong(entryLogPositionOffset(entryOffset), logPosition);
        indexBuffer.putLong(entryAddressOffset(entryOffset), storageAddr);

        // increment size
        indexBuffer.putIntOrdered(indexSizeOffset(), newIndexSize);

        return newIndexSize;
    }

    /**
     * Truncates the block index up to the given position.
     *
     * If the following indices are given:
     * <ul>
     * <li>position: 10 / address: 100</li>
     * <li>position: 20 / address: 200</li>
     * <li>position: 30 / address: 300</li>
     * </ul>
     *
     * Then
     * <ul>
     * <li><code>truncate(5)</code> and <code>truncate(10)</code> would truncate all existing indices</li>
     * <li><code>truncate(20)</code> would truncate existing block indices beginning from block index with position 20</li>
     * <li><code>truncate(25)</code> would truncate existing block indices beginning from next block index with position 30</li>
     * <li><code>truncate(30)</code> would truncate last existing block index</li>
     * <li><code>truncate(35)</code> would not do anything</li>
     * </ul>
     *
     * @param position The position at which to truncate the block index.
     */
    public void truncate(long position)
    {
        final int size = size();

        if (size > 0)
        {
            int index = Math.max(lookupIndex(position), 0);
            int entryOffset = entryOffset(index);

            final long entryValue = indexBuffer.getLong(entryLogPositionOffset(entryOffset));
            if (entryValue < position)
            {
                index++;
                entryOffset += entryLength();
            }

            final int lastEntryIndex = size - 1;
            if (lastEntryIndex >= index)
            {
                final int capacity = indexBuffer.capacity();
                final int length = capacity - entryOffset;

                indexBuffer.putIntVolatile(indexSizeOffset(), index);
                indexBuffer.setMemory(entryOffset, length, (byte) 0);
            }
        }
    }

    /**
     * @return the current size of the index
     */
    public int size()
    {
        return indexBuffer.getIntVolatile(indexSizeOffset());
    }

    /**
     * @return the capacity of the index
     */
    public int capacity()
    {
        return capacity;
    }

    public long getLogPosition(int idx)
    {
        boundsCheck(idx, size());

        final int entryOffset = entryOffset(idx);

        return indexBuffer.getLong(entryLogPositionOffset(entryOffset));
    }

    public long getAddress(int idx)
    {
        boundsCheck(idx, size());

        final int entryOffset = entryOffset(idx);

        return indexBuffer.getLong(entryAddressOffset(entryOffset));
    }

    private static void boundsCheck(int idx, int size)
    {
        if (idx < 0 || idx >= size)
        {
            throw new IllegalArgumentException(String.format("Index out of bounds. index=%d, size=%d.", idx, size));
        }
    }

    public void recover(LogStorage logStorage, int indexBlockSize)
    {
        recover(logStorage, 0, indexBlockSize);
    }

    public void recover(LogStorage logStorage, long startPosition, int indexBlockSize)
    {
        final ByteBuffer readBuffer = ByteBuffer.allocateDirect(indexBlockSize);
        final UnsafeBuffer readBufferView = new UnsafeBuffer(readBuffer);

        final LoggedEventImpl logEntry = new LoggedEventImpl();

        int currentBlockSize = 0;

        long readAddress = logStorage.getFirstBlockAddress();
        if (startPosition > 0)
        {
            // start reading from address of given start position
            readAddress = Math.max(lookupBlockAddress(startPosition), readAddress);
        }

        while (readAddress > 0)
        {
            long nextReadAddress = logStorage.read(readBuffer, readAddress);

            if (nextReadAddress > 0)
            {
                int available = readBuffer.position();
                int offset = 0;

                currentBlockSize += available;

                // read all fragments of the block
                while (offset < available)
                {
                    final int read = available - offset;

                    if (read < HEADER_LENGTH)
                    {
                        // end of block - read a partly header

                        // read remainder of header
                        readBuffer.limit(available);
                        readBuffer.position(offset);
                        readBuffer.compact();
                        readBuffer.limit(HEADER_LENGTH);

                        nextReadAddress = logStorage.read(readBuffer, nextReadAddress);

                        // continue reading the rest of the fragment
                        offset = 0;
                        available = HEADER_LENGTH;
                    }

                    // read the log entry
                    logEntry.wrap(readBufferView, offset);

                    final int fragmentLength = logEntry.getFragmentLength();

                    if (read >= fragmentLength)
                    {
                        final long position = logEntry.getPosition();

                        // create index of completely read log entry
                        // - if index size is reached
                        if (currentBlockSize >= indexBlockSize && position > startPosition)
                        {
                            addBlock(position, readAddress);

                            currentBlockSize = 0;
                        }
                    }
                    else
                    {
                        // end of block - read a partly log entry or only the header

                        // read the remainder of the fragment
                        readBuffer.position(offset);
                        readBuffer.limit(available);
                        readBuffer.compact();

                        readBuffer.limit(fragmentLength);
                        nextReadAddress = logStorage.read(readBuffer, nextReadAddress);

                        // reset the buffer
                        readBufferView.setMemory(0, readBuffer.capacity(), (byte) 0);
                    }

                    // continue with next fragment
                    offset += fragmentLength;
                }
            }
            // continue with next block
            readAddress = nextReadAddress;
            readBuffer.clear();
        }
    }

    @Override
    public void writeSnapshot(OutputStream outputStream) throws Exception
    {
        StreamUtil.write(indexBuffer, outputStream);
    }

    @Override
    public void recoverFromSnapshot(InputStream inputStream) throws Exception
    {
        final byte[] byteArray = StreamUtil.read(inputStream);

        indexBuffer.putBytes(0, byteArray);
    }

    @Override
    public void reset()
    {
        // verify alignment to ensure atomicity of updates to the index metadata
        indexBuffer.verifyAlignment();

        // set initial size
        indexBuffer.putIntVolatile(indexSizeOffset(), 0);

        indexBuffer.setMemory(dataOffset(), capacity * entryLength(), (byte) 0);
    }

}
