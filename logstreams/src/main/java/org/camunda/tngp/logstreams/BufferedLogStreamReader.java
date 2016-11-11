package org.camunda.tngp.logstreams;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.typeOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.versionOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.HEADER_BLOCK_LENGHT;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.headerLength;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.keyLengthOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.keyOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.positionOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.valueOffset;
import static org.camunda.tngp.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;

import java.nio.ByteBuffer;
import java.util.NoSuchElementException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.impl.LogBlockIndex;
import org.camunda.tngp.logstreams.impl.ReadableFragment;
import org.camunda.tngp.logstreams.impl.StreamContext;
import org.camunda.tngp.logstreams.impl.StreamImpl;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.camunda.tngp.util.buffer.BufferReader;

public class BufferedLogStreamReader implements LogStreamReader
{
    protected static final int DEFAULT_INITIAL_BUFFER_CAPACITY = 1024 * 32;

    protected enum IteratorState
    {
        UNINITIALIZED,
        INITIALIZED,
        INITIALIZED_EMPTY_LOG,
        ACTIVE;
    }

    protected final LoggedEventImpl curr = new LoggedEventImpl();

    protected final int headerLength = HEADER_BLOCK_LENGHT + HEADER_LENGTH;
    protected final DirectBuffer buffer = new UnsafeBuffer(0, 0);

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

    public BufferedLogStreamReader(int initialBufferCapacity)
    {
        this.ioBuffer = ByteBuffer.allocateDirect(initialBufferCapacity);
        this.buffer.wrap(ioBuffer);
    }

    public BufferedLogStreamReader(int initialBufferCapacity, LogStream log)
    {
        this(initialBufferCapacity);
        wrap(log);
    }

    public BufferedLogStreamReader(LogStream logStream)
    {
        this(((StreamImpl)logStream).getContext());
    }

    public BufferedLogStreamReader(StreamContext ctx)
    {
        this(DEFAULT_INITIAL_BUFFER_CAPACITY);
        wrap(ctx);
    }

    @Override
    public void wrap(LogStream logStream)
    {
        wrap(((StreamImpl)logStream).getContext());
    }

    public void wrap(StreamContext ctx)
    {
        initLog(ctx);
        seekToLastEvent();
    }

    @Override
    public void wrap(LogStream logStream, long position)
    {
        wrap(((StreamImpl)logStream).getContext(), position);
    }

    public void wrap(StreamContext ctx, long position)
    {
        initLog(ctx);
        seek(position);
    }

    private void initLog(StreamContext ctx)
    {
        this.logStorage = ctx.getLogStorage();
        this.blockIndex = ctx.getBlockIndex();
    }

    public void clear()
    {
        curr.wrap(buffer, -1);
        available = 0;
        nextReadAddr = -1;
        iteratorState = IteratorState.UNINITIALIZED;
    }

    public void seek(long seekPosition)
    {
        clear();

        final int indexSize = blockIndex.size();
        if (indexSize == 0)
        {
            this.iteratorState = IteratorState.INITIALIZED_EMPTY_LOG;
            return;
        }

        nextReadAddr = blockIndex.lookupBlockAddress(seekPosition);

        if (nextReadAddr < 0)
        {
            clear();
            return;
        }

        // read at least header of initial fragment
        if (!readMore(headerLength))
        {
            clear();
            return;
        }

        final int fragmentLength = curr.getFragmentLength();

        // ensure fragment is fully read
        if (available < fragmentLength)
        {
            if (!readMore(available - fragmentLength))
            {
                clear();
                return;
            }
        }

        iteratorState = IteratorState.INITIALIZED;

        while (hasNext())
        {
            final LoggedEvent entry  = next();

            if (entry.getPosition() >= seekPosition)
            {
                iteratorState = IteratorState.INITIALIZED;
                return;
            }
        }

        iteratorState = IteratorState.ACTIVE;
    }

    @Override
    public void seekToFirstEvent()
    {
        final int size = blockIndex.size();

        if (size == 0)
        {
            this.iteratorState = IteratorState.INITIALIZED_EMPTY_LOG;
        }
        else
        {
            seek(blockIndex.getLogPosition(0));
            this.iteratorState = IteratorState.INITIALIZED;
        }
    }

    @Override
    public void seekToLastEvent()
    {
        final int size = blockIndex.size();

        if (size == 0)
        {
            this.iteratorState = IteratorState.INITIALIZED_EMPTY_LOG;
        }
        else
        {
            final int lastIdx = size - 1;
            final long lastBlockPosition =  blockIndex.getLogPosition(lastIdx);

            seek(lastBlockPosition);

            // advance until end of block
            while (hasNext())
            {
                next();
            }

            // will return last entry again
            this.iteratorState = IteratorState.INITIALIZED;
        }
    }

    protected boolean readMore(int minBytes)
    {
        final int initialPosition = curr.fragmentOffset;

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

    public void ensureRemainingBufferCapacity(int requiredCapacity)
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
        if (iteratorState == IteratorState.UNINITIALIZED)
        {
            throw new IllegalStateException("Iterator not initialized");
        }

        if (iteratorState == IteratorState.INITIALIZED)
        {
            return true;
        }

        if (iteratorState == IteratorState.INITIALIZED_EMPTY_LOG)
        {
            seekToFirstEvent();
            return iteratorState == IteratorState.INITIALIZED;
        }

        final int fragmentLength = curr.getFragmentLength();
        int nextFragmentOffset = curr.fragmentOffset + fragmentLength;
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

        return true;
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
            final int offset = curr.fragmentOffset;
            final int fragmentLength = curr.getFragmentLength();

            curr.wrap(buffer, offset + fragmentLength);

            return curr;
        }
    }

    @Override
    public long getPosition()
    {
        return curr.getPosition();
    }

    public static class LoggedEventImpl implements ReadableFragment, LoggedEvent

    {
        protected int fragmentOffset = -1;
        protected DirectBuffer buffer;

        public void wrap(DirectBuffer buffer, int offset)
        {
            this.fragmentOffset = offset;
            this.buffer = buffer;
        }

        @Override
        public int getType()
        {
            return buffer.getShort(typeOffset(fragmentOffset));
        }

        @Override
        public int getVersion()
        {
            return buffer.getShort(versionOffset(fragmentOffset));
        }

        @Override
        public int getMessageLength()
        {
            return buffer.getInt(lengthOffset(fragmentOffset));
        }

        @Override
        public int getMessageOffset()
        {
            return messageOffset(fragmentOffset);
        }

        @Override
        public int getStreamId()
        {
            return buffer.getInt(streamIdOffset(fragmentOffset));
        }

        @Override
        public DirectBuffer getBuffer()
        {
            return buffer;
        }

        public int getFragmentLength()
        {
            return alignedLength(getMessageLength());
        }

        @Override
        public long getPosition()
        {
            return buffer.getLong(positionOffset(messageOffset(fragmentOffset)));
        }

        @Override
        public long getLongKey()
        {
            return buffer.getLong(keyOffset(messageOffset(fragmentOffset)));
        }

        @Override
        public DirectBuffer getValueBuffer()
        {
            return buffer;
        }

        @Override
        public int getValueOffset()
        {
            final int entryHeaderOffset = messageOffset(fragmentOffset);
            final int keyLength = buffer.getShort(keyLengthOffset(entryHeaderOffset));

            return valueOffset(entryHeaderOffset, keyLength);
        }

        @Override
        public int getValueLength()
        {
            final int entryHeaderOffset = messageOffset(fragmentOffset);
            final int keyLength = buffer.getShort(keyLengthOffset(entryHeaderOffset));

            return buffer.getInt(lengthOffset(fragmentOffset)) - headerLength(keyLength);
        }

        @Override
        public void readValue(BufferReader reader)
        {
            reader.wrap(buffer, getValueOffset(), getValueLength());
        }

    }

}
