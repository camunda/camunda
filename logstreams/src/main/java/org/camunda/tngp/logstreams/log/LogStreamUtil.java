package org.camunda.tngp.logstreams.log;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.spi.LogStorage;

import java.nio.ByteBuffer;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.*;

/**
 * Represents a class which contains some utilities for the log stream.
 */
public class LogStreamUtil
{
    public static final int INVALID_ADDRESS = -1;
    public static final int MAX_READ_EVENT_SIZE = 1024 * 32;

    public static long getAddressForPosition(LogStream stream, long position)
    {
        final LogEntryAddressSupplier logEntryAddressSupplier = LogEntryAddressSupplier.getInstance();
        logEntryAddressSupplier.wrap(stream);
        return logEntryAddressSupplier.getAddress(position);
    }

    private static final class LogEntryAddressSupplier
    {
        private static final class InstanceHolder
        {
            static final LogEntryAddressSupplier INSTANCE = new LogEntryAddressSupplier();
        }

        public static LogEntryAddressSupplier getInstance()
        {
            return InstanceHolder.INSTANCE;
        }

        protected LogStream logStream;
        protected LogBlockIndex blockIndex;
        protected LogStorage logStorage;

        protected final ByteBuffer ioBuffer = ByteBuffer.allocateDirect(MAX_READ_EVENT_SIZE);
        protected final DirectBuffer buffer = new UnsafeBuffer(0, 0);

        protected long nextReadAddress = INVALID_ADDRESS;
        protected long currentAddress = INVALID_ADDRESS;

        private LogEntryAddressSupplier()
        {
            buffer.wrap(ioBuffer);
        }

        public void wrap(LogStream logStream)
        {
            this.logStream = logStream;
            this.blockIndex = logStream.getLogBlockIndex();
            this.logStorage = logStream.getLogStorage();
            clear();
        }

        protected void clear()
        {
            nextReadAddress = INVALID_ADDRESS;
            currentAddress = INVALID_ADDRESS;
        }

        public long getAddress(final long position)
        {
            clear();

            if (!findStartAddress(position))
            {
                return nextReadAddress;
            }

            return findAddress(position);
        }

        private boolean findStartAddress(long position)
        {
            final int indexSize = blockIndex.size();
            if (indexSize > 0)
            {
                nextReadAddress = blockIndex.lookupBlockAddress(position);
            }
            else
            {
                // fallback: get first block address
                nextReadAddress = logStorage.getFirstBlockAddress();

                if (nextReadAddress == INVALID_ADDRESS)
                {
                    return false;
                }
            }
            return true;
        }

        private long findAddress(long position)
        {
            long address = INVALID_ADDRESS;
            boolean hasNext = next();
            while (hasNext)
            {
                final long currentPosition = getPosition(buffer, 0);
                if (currentPosition < position)
                {
                    hasNext = next();
                }
                else
                {
                    hasNext = false;
                    if (currentPosition >= position)
                    {
                        address = currentAddress;
                    }
                }
            }
            return address;
        }

        protected boolean next()
        {
            currentAddress = nextReadAddress;

            if (!readHeader())
            {
                return false;
            }

            final int fragmentLength = getFragmentLength(buffer, 0);
            return readMessage(fragmentLength - HEADER_LENGTH);
        }

        protected boolean readHeader()
        {
            return read(HEADER_LENGTH);
        }

        protected boolean readMessage(final int fragmentLength)
        {
            int remainingBytes = fragmentLength;
            while (remainingBytes > 0)
            {
                final int limit = Math.min(remainingBytes, buffer.capacity());
                read(limit);
                remainingBytes -= limit;
            }
            return true;
        }

        protected boolean read(final int limit)
        {
            ioBuffer.position(0);
            ioBuffer.limit(limit);

            final long opResult = logStorage.read(ioBuffer, nextReadAddress);

            if (opResult >= 0)
            {
                nextReadAddress = opResult;
                return true;
            }

            return false;
        }
    }
}
