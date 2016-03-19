package org.camunda.tngp.log;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.camunda.tngp.dispatcher.BlockHandler;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.junit.Test;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TestLogAppender
{

    public class BlockCompletionHandler implements BlockHandler
    {

        int fragmentLength = 0;

        int workCountDown = 0;

        public BlockCompletionHandler(int totalWork, int frameLength)
        {
            this.workCountDown = totalWork;
            this.fragmentLength = frameLength;
        }

        @Override
        public void onBlockAvailable(ByteBuffer buffer, int blockOffset, int blockLength, int streamId,
                long blockPosition)
        {
            workCountDown -= (blockLength / fragmentLength);
        }

        public boolean isDone()
        {
            return workCountDown <= 0;
        }
    }

    public class LogFragmentReader implements LogFragmentHandler
    {
        public void onFragment(long position, FileChannel fileChannel, int offset, int length)
        {
            System.out.println(position);
        }
    }

    @Test
    public void shouldAppend() throws InterruptedException
    {
        final Log log = createLog();

        final Dispatcher writeBuffer = log.getWriteBuffer();

        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

        final int totalWork = 10000000;

        final BlockCompletionHandler blockCompletionHandler = new BlockCompletionHandler(totalWork, alignedLength(msg.capacity()));

        while(!blockCompletionHandler.isDone())
        {
            writeBuffer.offer(msg);
            writeBuffer.pollBlock(1, blockCompletionHandler, totalWork, false);
        }
    }


    @Test
    public void shouldPoll() throws InterruptedException
    {
        final Log log = createLog();

        final Dispatcher writeBuffer = log.getWriteBuffer();

        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

        int work = 1000;

        final BlockCompletionHandler blockCompletionHandler = new BlockCompletionHandler(work, alignedLength(msg.capacity()));

        while(!blockCompletionHandler.isDone())
        {
            if(work > 0)
            {
                msg.putInt(0, work);
                writeBuffer.offer(msg);
                work--;
            }
            writeBuffer.pollBlock(1, blockCompletionHandler, work, false);
        }

        LogFragmentReader reader = new LogFragmentReader();
        long nextOffset = log.pollFragment(log.getInitialPosition(), reader);
    }

    private Log createLog()
    {
        final File logRoot = new File("/tmp/logs/foo");
        if(logRoot.exists())
        {
            File[] logFiles = logRoot.listFiles((f) -> f.getName().endsWith(".data"));
            for (int i = 0; i < logFiles.length; i++)
            {
                logFiles[i].delete();
            }
        }
        else
        {
            logRoot.mkdirs();
        }

        return Logs.createLog("foo")
                .logRootPath("/tmp/logs")
                .logFragementSize(1024 * 1024 * 512)
                .writeBufferSize(1024 * 1024 * 128)
                .build();
    }

}
