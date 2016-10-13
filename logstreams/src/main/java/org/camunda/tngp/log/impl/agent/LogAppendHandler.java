package org.camunda.tngp.log.impl.agent;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.log.impl.LogEntryDescriptor.positionOffset;

import java.nio.ByteBuffer;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.log.impl.LogBlockIndex;
import org.camunda.tngp.log.impl.LogContext;
import org.camunda.tngp.log.spi.LogStorage;

public class LogAppendHandler
{
    protected final LogBlockIndex blockIndex;
    protected final LogStorage logStorage;

    protected int blockSize = 1024 * 1024 * 64;

    protected int currentBlockSize;

    public LogAppendHandler(LogContext logContext)
    {
        blockIndex = logContext.getBlockIndex();
        logStorage = logContext.getLogStorage();
    }

    public int append(BlockPeek blockPeek)
    {
        int bytesWritten = 0;

        final ByteBuffer ioBuffer = blockPeek.getRawBuffer();

        final long opResult = logStorage.append(ioBuffer);

        if (opResult >= 0)
        {
            // read position of first entry
            final MutableDirectBuffer buffer = blockPeek.getBuffer();
            final long postion = buffer.getLong(positionOffset(messageOffset(0)));
            bytesWritten += blockPeek.getBlockLength();

            // TODO: do not add every block written
            onBlockWritten(postion, opResult, blockPeek.getBlockLength());
            blockPeek.markCompleted();
        }
        else
        {
            System.err.println("log append failed");
            blockPeek.markFailed();
        }

        return bytesWritten;
    }

    protected void onBlockWritten(long postion, long addr, int blockLength)
    {
        if (currentBlockSize == 0)
        {
            blockIndex.addBlock(postion, addr);
        }

        final int newBlockSize = currentBlockSize + blockLength;
        if (newBlockSize > blockSize)
        {
            currentBlockSize = 0;
        }
        else
        {
            currentBlockSize = newBlockSize;
        }

    }

}
