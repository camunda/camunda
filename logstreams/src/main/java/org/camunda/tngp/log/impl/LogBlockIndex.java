package org.camunda.tngp.log.impl;

import static org.camunda.tngp.log.impl.LogBlockIndexDescriptor.*;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

import java.nio.ByteBuffer;
import java.util.function.Function;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.log.BufferedLogReader.ReadableLogEntryImpl;
import org.camunda.tngp.log.spi.LogStorage;

/**
 * Block index, mapping a log entry's position to the physical address of the block in which it resides
 * in log storage.
 *<p>
 * The log is structured into blocks. A block contains one or multiple log entries.
 *<p>
 * Each LogEntry has a position inside the log. This position addresses it uniquely and is assigned
 * when the entry is first written into the log. The position never changes and is preserved even if the log is
 * compacted.
 *<p>
 * In order to read a log entry, the position must be translated into the "physical address" of the
 * block in which it resides in log storage. Then, the block can be scanned for the log entry.
 *
 */
public class LogBlockIndex
{
    protected final AtomicBuffer indexBuffer;

    protected final int capacity;

    protected long lastVirtualPosition = -1;

    public LogBlockIndex(int capacity, Function<Integer, AtomicBuffer> bufferAllocator)
    {
        final int requiredBufferCapacity = dataOffset() + (capacity * entryLength());

        this.indexBuffer = bufferAllocator.apply(requiredBufferCapacity);
        this.capacity = capacity;

        // verify alignment to ensure atomicity of updates to the index metadata
        indexBuffer.verifyAlignment();

        // set initial size
        indexBuffer.putIntVolatile(indexSizeOffset(), 0);
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
        final int lastEntryIdx = size() - 1;

        int low = 0;
        int high = lastEntryIdx;

        long pos = -1;

        while (low <= high)
        {
            final int mid = (low + high) >>> 1;
            final int entryOffset = entryOffset(mid);

            if (mid == lastEntryIdx)
            {
                pos = indexBuffer.getLong(entryAddressOffset(entryOffset));
                break;
            }
            else
            {
                final long entryValue = indexBuffer.getLong(entryLogPositionOffset(entryOffset));
                final long nextEntryValue = indexBuffer.getLong(entryLogPositionOffset(entryOffset(mid + 1)));

                if (entryValue <= position && position < nextEntryValue)
                {
                    pos = indexBuffer.getLong(entryAddressOffset(entryOffset));
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

        return pos;
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
            final String errorMessage = String.format("Illegal value for virtual position.Value=%d, last value in index=%d. Must provide positions in ascending order.", logPosition, lastVirtualPosition);
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

    public void recover(LogImpl log)
    {
        final LogStorage logStorage = log.getLogContext().getLogStorage();
        final ByteBuffer readBuffer = ByteBuffer.allocateDirect(1024 * 1024 * 4);
        final UnsafeBuffer readBufferView = new UnsafeBuffer(readBuffer);

        final ReadableLogEntryImpl logEntry = new ReadableLogEntryImpl();
        logEntry.wrap(readBufferView, 0);

        long readAddress = logStorage.getFirstBlockAddress();

        while (readAddress > 0)
        {
            readBuffer.clear();
            long nextReadAddress = logStorage.read(readBuffer, readAddress);

            if (nextReadAddress > 0)
            {
                int available = readBuffer.position();
                int offset = 0;

                while ((available - offset) > 0)
                {
                    if ((available - offset) < HEADER_LENGTH)
                    {
                        // read remainder of header
                        readBuffer.limit(available);
                        readBuffer.position(offset);
                        readBuffer.compact();
                        readBuffer.limit(HEADER_LENGTH);

                        nextReadAddress = logStorage.read(readBuffer, nextReadAddress);

                        offset = 0;
                        available = HEADER_LENGTH;
                    }

                    logEntry.wrap(readBufferView, offset);

                    final int fragmentLength = logEntry.getFragmentLength();

                    if ((available - offset) >= fragmentLength)
                    {
                        if (offset == 0)
                        {
                            final long position = logEntry.getPosition();
                            addBlock(position, readAddress);
                        }

                        offset += fragmentLength;
                    }
                    else
                    {
                        // read the remainder of the fragment
                        readBuffer.position(offset);
                        readBuffer.limit(available);
                        readBuffer.compact();
                        readBuffer.limit(fragmentLength);
                        nextReadAddress = logStorage.read(readBuffer, nextReadAddress);
                        readBufferView.setMemory(0, readBuffer.capacity(), (byte) 0);
                        available = 0;
                        offset = 0;
                    }
                }
            }
            readAddress = nextReadAddress;
        }

    }

}
