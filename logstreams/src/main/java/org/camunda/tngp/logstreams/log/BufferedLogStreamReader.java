package org.camunda.tngp.logstreams.log;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.*;
import static org.camunda.tngp.logstreams.spi.LogStorage.*;

import java.nio.ByteBuffer;
import java.util.NoSuchElementException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.impl.LoggedEventImpl;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.spi.LogStorage;

public class BufferedLogStreamReader implements LogStreamReader
{
    protected static final int DEFAULT_INITIAL_BUFFER_CAPACITY = 1024 * 32;

    protected enum IteratorState
    {
        UNINITIALIZED,
        INITIALIZED,
        INITIALIZED_EMPTY_LOG,
        ACTIVE,
        NOT_COMMITTED;
    }

    protected final LoggedEventImpl curr = new LoggedEventImpl();

    protected final int headerLength = HEADER_BLOCK_LENGTH + HEADER_LENGTH;
    protected final DirectBuffer buffer = new UnsafeBuffer(0, 0);

    protected boolean readUncommittedEntries;

    protected LogStream logStream;
    protected LogStorage logStorage;
    protected LogBlockIndex blockIndex;

    protected ByteBuffer ioBuffer;
    protected int available;
    protected long nextReadAddr;

    protected IteratorState iteratorState = IteratorState.UNINITIALIZED;

    public BufferedLogStreamReader()
    {
        this(DEFAULT_INITIAL_BUFFER_CAPACITY);
    }

    public BufferedLogStreamReader(boolean readUncommittedEntries)
    {
        this(DEFAULT_INITIAL_BUFFER_CAPACITY, readUncommittedEntries);
    }

    public BufferedLogStreamReader(int initialBufferCapacity)
    {
        this(initialBufferCapacity, false);
    }

    public BufferedLogStreamReader(int initialBufferCapacity, boolean readUncommittedEntries)
    {
        this.ioBuffer = ByteBuffer.allocateDirect(initialBufferCapacity);
        this.buffer.wrap(ioBuffer);
        this.readUncommittedEntries = readUncommittedEntries;
    }

    public BufferedLogStreamReader(int initialBufferCapacity, LogStream logStream)
    {
        this(initialBufferCapacity);
        wrap(logStream);
    }

    public BufferedLogStreamReader(int initialBufferCapacity, LogStream logStream, boolean readUncommittedEntries)
    {
        this(initialBufferCapacity, readUncommittedEntries);
        wrap(logStream);
    }

    public BufferedLogStreamReader(LogStream logStream)
    {
        this(DEFAULT_INITIAL_BUFFER_CAPACITY, logStream);
    }

    public BufferedLogStreamReader(LogStream logStream, boolean readUncommittedEntries)
    {
        this(DEFAULT_INITIAL_BUFFER_CAPACITY, logStream, readUncommittedEntries);
    }

    public BufferedLogStreamReader(LogStorage logStorage, LogBlockIndex blockIndex)
    {
        this(DEFAULT_INITIAL_BUFFER_CAPACITY);
        this.readUncommittedEntries = true;
        wrap(logStorage, blockIndex);
    }

    @Override
    public void wrap(LogStream logStream)
    {
        initReader(logStream);
        seekToFirstEvent();
    }

    @Override
    public void wrap(LogStream logStream, long position)
    {
        initReader(logStream);
        seek(position);
    }

    public void wrap(LogStorage logStorage, LogBlockIndex blockIndex)
    {
        initReader(logStorage, blockIndex);
        seekToFirstEvent();
    }

    protected void initReader(LogStream logStream)
    {
        final LogStorage logStorage = logStream.getLogStorage();
        final LogBlockIndex blockIndex = logStream.getLogBlockIndex();

        this.logStorage = logStorage;
        this.blockIndex = blockIndex;
        this.logStream = logStream;
    }

    protected void initReader(LogStorage logStorage, LogBlockIndex blockIndex)
    {
        this.logStorage = logStorage;
        this.blockIndex = blockIndex;
        this.logStream = null;
    }

    protected void clear()
    {
        curr.wrap(buffer, -1);
        available = 0;
        nextReadAddr = -1;
        iteratorState = IteratorState.UNINITIALIZED;
    }

    public boolean seek(long seekPosition)
    {
        clear();

        final long commitPosition = getCommitPosition();

        if (commitPosition < 0)
        {
            // negative commit position -> nothing is committed
            iteratorState = IteratorState.INITIALIZED_EMPTY_LOG;
            return false;
        }

        nextReadAddr = blockIndex.lookupBlockAddress(seekPosition);

        if (nextReadAddr < 0)
        {
            // fallback: seek without index
            nextReadAddr = logStorage.getFirstBlockAddress();

            if (nextReadAddr == -1)
            {
                this.iteratorState = IteratorState.INITIALIZED_EMPTY_LOG;
                return false;
            }
        }

        if (nextReadAddr < 0)
        {
            clear();
            return false;
        }

        // read at least header of initial fragment
        if (!readMore(headerLength))
        {
            clear();
            return false;
        }

        final int fragmentLength = curr.getFragmentLength();

        // ensure fragment is fully read
        if (available < fragmentLength)
        {
            if (!readMore(available - fragmentLength))
            {
                clear();
                return false;
            }
        }

        final long currPosition = curr.getPosition();

        if (commitPosition < currPosition)
        {
            iteratorState = IteratorState.NOT_COMMITTED;
            return false;
        }

        iteratorState = IteratorState.INITIALIZED;

        do
        {
            final LoggedEvent entry = next();
            final long entryPosition = entry.getPosition();

            if (entryPosition >= seekPosition)
            {
                iteratorState = IteratorState.INITIALIZED;
                return entryPosition == seekPosition;
            }

        }
        while (hasNext());

        iteratorState = IteratorState.ACTIVE;
        return false;
    }

    @Override
    public void seekToFirstEvent()
    {
        final int size = blockIndex.size();

        if (size > 0)
        {
            final long seekPosition = blockIndex.getLogPosition(0);
            seek(seekPosition);

            if (iteratorState == IteratorState.ACTIVE)
            {
                iteratorState = IteratorState.INITIALIZED;
            }

        }
        else
        {
            // fallback: seek without index
            seek(Long.MIN_VALUE);
        }
    }

    @Override
    public void seekToLastEvent()
    {
        final long commitPosition = getCommitPosition();

        seek(commitPosition);

        if (iteratorState == IteratorState.ACTIVE)
        {
            // will return last entry again
            this.iteratorState = IteratorState.INITIALIZED;
        }
    }

    protected boolean readMore(int minBytes)
    {
        final int initialPosition = curr.getFragmentOffset();

        if (initialPosition >= 0)
        {
            // compact remaining data to the beginning of the buffer
            ioBuffer.limit(available);
            ioBuffer.position(initialPosition);
            ioBuffer.compact();
            available -= initialPosition;
        }
        else
        {
            ioBuffer.clear();
        }

        curr.wrap(buffer, 0);

        ensureRemainingBufferCapacity(minBytes);

        int bytesRead = 0;

        while (bytesRead < minBytes)
        {
            final long opResult = logStorage.read(ioBuffer, nextReadAddr);

            if (opResult >= 0)
            {
                bytesRead += ioBuffer.position() - available;
                available += bytesRead;
                nextReadAddr = opResult;
            }
            else if (opResult == OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY)
            {
                ensureRemainingBufferCapacity(ioBuffer.capacity() * 2);
            }
            else
            {
                break;
            }
        }

        return bytesRead >= minBytes;
    }

    protected void ensureRemainingBufferCapacity(int requiredCapacity)
    {
        if (ioBuffer.remaining() < requiredCapacity)
        {
            final int pos = ioBuffer.position();
            final ByteBuffer newBuffer = ByteBuffer.allocateDirect(pos + requiredCapacity);

            if (pos > 0)
            {
                // copy remaining data
                ioBuffer.flip();
                newBuffer.put(ioBuffer);
            }

            newBuffer.limit(newBuffer.capacity());
            newBuffer.position(pos);

            ioBuffer = newBuffer;
        }
    }

    public boolean hasNext()
    {
        ensureInitialized();

        if (iteratorState == IteratorState.INITIALIZED)
        {
            return true;
        }

        if (iteratorState == IteratorState.INITIALIZED_EMPTY_LOG)
        {
            seekToFirstEvent();
            return iteratorState == IteratorState.INITIALIZED;
        }

        if (iteratorState == IteratorState.NOT_COMMITTED)
        {
            final long currentPosition = curr.getPosition();
            if (canReadPosition(currentPosition))
            {
                iteratorState = IteratorState.INITIALIZED;
                return true;
            }
            else
            {
                return false;
            }
        }

        final int fragmentLength = curr.getFragmentLength();
        int nextFragmentOffset = curr.getFragmentOffset() + fragmentLength;
        final int nextHeaderEnd = nextFragmentOffset + headerLength;

        if (available < nextHeaderEnd)
        {
            // Attempt to read at least next header
            if (readMore(nextHeaderEnd - available))
            {
                // reading more data moved offset of next fragment to the left
                nextFragmentOffset = fragmentLength;
            }
            else
            {
                return false;
            }
        }

        final int nextFragmentLength = alignedLength(buffer.getInt(lengthOffset(nextFragmentOffset)));
        final int nextFragmentEnd = nextFragmentOffset + nextFragmentLength;

        if (available < nextFragmentEnd)
        {
            // Attempt to read remainder of fragment
            if (!readMore(nextFragmentEnd - available))
            {
                return false;
            }
        }

        final int nextFragmentLogEntryHeader = nextFragmentOffset + HEADER_LENGTH;
        final long nextFragmentPosition = buffer.getLong(positionOffset(nextFragmentLogEntryHeader));

        return canReadPosition(nextFragmentPosition);
    }

    protected boolean canReadPosition(long position)
    {
        final long commitPosition = getCommitPosition();
        return commitPosition >= position;
    }

    protected long getCommitPosition()
    {
        long commitPosition = Long.MAX_VALUE;

        if (!readUncommittedEntries)
        {
            commitPosition = logStream.getCommitPosition();
        }

        return commitPosition;
    }

    protected void ensureInitialized()
    {
        if (iteratorState == IteratorState.UNINITIALIZED)
        {
            throw new IllegalStateException("Iterator not initialized");
        }
    }

    public LoggedEvent next()
    {
        if (!hasNext())
        {
            throw new NoSuchElementException("Api protocol violation: No next log entry available; You need to probe with hasNext() first.");
        }

        if (iteratorState == IteratorState.INITIALIZED)
        {
            iteratorState = IteratorState.ACTIVE;
            return curr;
        }
        else
        {
            final int offset = curr.getFragmentOffset();
            final int fragmentLength = curr.getFragmentLength();

            curr.wrap(buffer, offset + fragmentLength);

            return curr;
        }
    }

    @Override
    public long getPosition()
    {
        ensureInitialized();

        if (iteratorState == IteratorState.INITIALIZED_EMPTY_LOG || iteratorState == IteratorState.NOT_COMMITTED)
        {
            throw new NoSuchElementException("No log entry available.");
        }

        return curr.getPosition();
    }

}
