package io.zeebe.broker.clustering.raft.state;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.HEADER_BLOCK_LENGTH;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.LogStorage;

public class LogStreamState
{

    public static final Logger LOG = Loggers.CLUSTERING_LOGGER;


    protected final LogStream stream;
    protected final LogStorage logStorage;
    protected final LogBlockIndex blockIndex;

    protected final BufferedLogStreamReader reader;

    protected long lastWrittenPosition = -1L;
    protected int lastWrittenTerm = -1;

    protected long lastReceivedPosition = -1L;
    protected int lastReceivedTerm = -1;

    protected final UnsafeBuffer bufferedEntries;

    protected long firstBufferedEntryPosition = -1L;
    protected long lastFlush = -1L;

    protected int bufferedEntriesOffset;
    protected int currentBlockSize = 0;

    protected final BrokerEventMetadata entryMetadata;
    protected final LoggedEntryAddressSupplier addressableStreamReader;

    public LogStreamState(final LogStream stream)
    {
        this.stream = stream;

        this.logStorage = stream.getLogStorage();
        this.blockIndex = stream.getLogBlockIndex();

        this.reader = new BufferedLogStreamReader(true);
        this.reader.wrap(stream);

        this.bufferedEntriesOffset = 0;
        this.bufferedEntries = new UnsafeBuffer(0, 0);
        bufferedEntries.wrap(ByteBuffer.allocateDirect(1024));

        this.entryMetadata = new BrokerEventMetadata();
        this.addressableStreamReader = new LoggedEntryAddressSupplier(stream);
    }

    public void reset()
    {
        flushBufferedEntries();

        reader.seekToLastEvent();
        if (reader.hasNext())
        {
            final LoggedEvent lastEntry = reader.next();
            lastEntry.readMetadata(entryMetadata);

            lastWrittenPosition = lastEntry.getPosition();
            lastWrittenTerm = entryMetadata.getRaftTermId();
        }

        lastReceivedPosition = lastWrittenPosition;
        lastReceivedTerm = lastWrittenTerm;
    }

    public long append(final LoggedEvent entry)
    {
        final LoggedEventImpl data = (LoggedEventImpl) entry;
        data.readMetadata(entryMetadata);

        final int capacity = bufferedEntries.capacity();
        final int remaining = capacity - bufferedEntriesOffset;

        final DirectBuffer value = data.getBuffer();
        final int dataLength = data.getFragmentLength();
        final int dataOffset = data.getFragmentOffset();

        if (remaining < dataLength && bufferedEntriesOffset > 0)
        {
            final long addr = flushBufferedEntries();
            if (addr < 1)
            {
                return addr;
            }
        }

        if (bufferedEntries.capacity() < dataLength)
        {
            // TODO: should we reduce buffer size to origin values?
            bufferedEntries.wrap(ByteBuffer.allocateDirect(dataLength));
        }

        bufferedEntries.putBytes(bufferedEntriesOffset, value, dataOffset, dataLength);
        bufferedEntriesOffset += dataLength;

        lastReceivedPosition = data.getPosition();
        lastReceivedTerm = entryMetadata.getRaftTermId();

        if (firstBufferedEntryPosition == -1)
        {
            firstBufferedEntryPosition = lastReceivedPosition;
        }

        return -100L; // buffered ;)
    }

    // TODO: this is a little bit odd
    public void setLastWrittenEntry(final long position, final int term)
    {
        discardBufferedEntries();

        if (reader.getPosition() != position)
        {
            reader.seek(position);
        }

        if (reader.hasNext())
        {
            final LoggedEvent nextEntry = reader.next();
            final long nextEntryPosition = nextEntry.getPosition();

            truncate(nextEntryPosition);

            lastWrittenPosition = position;
            lastWrittenTerm = term;

            lastReceivedPosition = lastWrittenPosition;
            lastReceivedTerm = lastWrittenTerm;
        }
    }

    public void truncate(final long position)
    {
        final long addr = addressableStreamReader.getAddress(position);

        if (addr >= 0)
        {
            LOG.debug("truncate log storage at address: {}", addr);
            logStorage.truncate(addr);
        }
        else
        {
            throw new RuntimeException("Address not found position: " + position);
        }
    }

    public long flushBufferedEntries()
    {
        long addr = -1L;

        if (bufferedEntriesOffset > 0)
        {
            final int blockLength = bufferedEntriesOffset;

            final ByteBuffer byteBuffer = bufferedEntries.byteBuffer();
            byteBuffer.position(0);
            byteBuffer.limit(blockLength);

            addr = logStorage.append(byteBuffer);

            if (addr > 0)
            {
                lastWrittenPosition = lastReceivedPosition;
                lastWrittenTerm = lastReceivedTerm;
            }

            discardBufferedEntries();
        }

        return addr;
    }

    public void discardBufferedEntries()
    {
        bufferedEntries.setMemory(0, bufferedEntriesOffset, (byte) 0);

        bufferedEntriesOffset = 0;
        firstBufferedEntryPosition = -1L;

        lastReceivedPosition = lastWrittenPosition;
        lastReceivedTerm = lastWrittenTerm;

        lastFlush = System.currentTimeMillis();
    }

    public boolean isLastReceivedEntry(final long logPosition, final int logTerm)
    {
        return lastReceivedPosition == logPosition && lastReceivedTerm == logTerm;
    }

    public boolean isLastWrittenEntry(final long logPosition, final int logTerm)
    {
        return lastWrittenPosition == logPosition && lastWrittenTerm == logTerm;
    }

    public boolean isBuffered(final long position)
    {
        return firstBufferedEntryPosition <= position && lastReceivedPosition >= position;
    }

    public boolean shouldFlushBufferedEntries()
    {
        return System.currentTimeMillis() >= lastFlush + 2000;
    }

    public boolean containsEntry(final long position, final long term)
    {
        boolean exists = false;

        reader.seek(position);
        if (reader.hasNext())
        {
            final LoggedEvent entry = reader.next();
            entry.readMetadata(entryMetadata);

            final long entryPosition = entry.getPosition();
            final long entryTerm = entryMetadata.getRaftTermId();

            exists = entryPosition == position && entryTerm == term;
        }

        return exists;
    }

    public long lastWrittenPosition()
    {
        return lastWrittenPosition;
    }

    public int lastWrittenTerm()
    {
        return lastWrittenTerm;
    }

    public long lastReceivedPosition()
    {
        return lastReceivedPosition;
    }

    public int lastReceivedTerm()
    {
        return lastReceivedTerm;
    }

    public LogStreamReader reader()
    {
        return reader;
    }

    class LoggedEntryAddressSupplier
    {
        protected final LogStream logStream;
        protected final LogBlockIndex blockIndex;
        protected final LogStorage logStorage;

        protected final int headerLength = HEADER_BLOCK_LENGTH + HEADER_LENGTH;
        protected final LoggedEventImpl curr = new LoggedEventImpl();

        protected final ByteBuffer ioBuffer = ByteBuffer.allocateDirect(1024 * 32);
        protected final DirectBuffer buffer = new UnsafeBuffer(0, 0);

        protected long nextReadAddr = -1;
        protected long currAddr = -1;

        LoggedEntryAddressSupplier(final LogStream logStream)
        {
            this.logStream = logStream;
            this.blockIndex = stream.getLogBlockIndex();
            this.logStorage = stream.getLogStorage();

            buffer.wrap(ioBuffer);
        }

        protected void clear()
        {
            curr.wrap(buffer, -1);
            nextReadAddr = -1;
            currAddr = -1;
        }

        public long getAddress(final long position)
        {
            clear();

            final int indexSize = blockIndex.size();
            if (indexSize > 0)
            {
                nextReadAddr = blockIndex.lookupBlockAddress(position);
            }
            else
            {
                // fallback: get first block address
                nextReadAddr = logStorage.getFirstBlockAddress();

                if (nextReadAddr == -1)
                {
                    return nextReadAddr;
                }
            }

            long addr = -1;

            while (next())
            {
                if (curr.getPosition() == position)
                {
                    addr = currAddr;
                    break;
                }

                if (curr.getPosition() > position)
                {
                    break;
                }
            }

            return addr;
        }

        protected boolean next()
        {
            currAddr = nextReadAddr;

            if (!readHeader())
            {
                return false;
            }

            final int fragmentLength = curr.getFragmentLength();

            return readMessage(fragmentLength);
        }

        protected boolean readHeader()
        {
            return read(headerLength, 0);
        }

        protected boolean readMessage(final int fragmentLength)
        {
            return read(fragmentLength, headerLength);
        }

        protected boolean read(final int limit, final int position)
        {
            ioBuffer.limit(limit);
            ioBuffer.position(position);

            curr.wrap(buffer, 0);

            final long opResult = logStorage.read(ioBuffer, nextReadAddr);

            if (opResult >= 0)
            {
                nextReadAddr = opResult;
                return true;
            }

            return false;
        }
    }
}
