package org.camunda.tngp.log;

import java.io.File;
import java.nio.ByteBuffer;

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

    @Test
    public void shouldAppend() throws InterruptedException
    {
        final File logRoot = new File("/tmp/test/");
        if(logRoot.exists())
        {
            File[] logFiles = logRoot.listFiles((f) -> f.getName().endsWith(".log"));
            for (int i = 0; i < logFiles.length; i++)
            {
                logFiles[i].delete();
            }
        }
        else
        {
            logRoot.mkdirs();
        }

        final Log log = Logs.createLog("foo")
            .logRootPath(logRoot.getAbsolutePath())
            .logFragementSize(1024 * 1024 * 512)
            .writeBufferSize(1024 * 1024 * 128)
            .build();

        final Dispatcher writeBuffer = log.getWriteBuffer();

        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

        final int totalWork = 1000000;

        final BlockCompletionHandler blockCompletionHandler = new BlockCompletionHandler(totalWork, alignedLength(msg.capacity()));

        while(!blockCompletionHandler.isDone())
        {
            writeBuffer.offer(msg);
            writeBuffer.pollBlock(1, blockCompletionHandler, totalWork, false);
        }
    }
}
