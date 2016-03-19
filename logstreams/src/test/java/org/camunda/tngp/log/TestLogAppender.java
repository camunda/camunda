package org.camunda.tngp.log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.LogBuilder.ThreadingMode;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TestLogAppender
{

    public class LogFragmentReader implements LogFragmentHandler
    {
        private ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        private UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);

        int lastId = -1;

        public void onFragment(long position, FileChannel fileChannel, int offset, int length)
        {
            buffer.position(0);
            buffer.limit(length);
            try
            {
                fileChannel.read(buffer, offset);
                int lastId = unsafeBuffer.getInt(0);
                if(lastId != this.lastId + 1)
                {
                    System.err.println("out of order: "+this.lastId + " "+lastId);
                }
                this.lastId = lastId;
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void shouldAppend() throws InterruptedException
    {
        final Log log = createLog();
        log.startSync();

        final Dispatcher writeBuffer = log.getWriteBuffer();

        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

        final LogFragmentReader logFragmentReader = new LogFragmentReader();

        final int totalWork = 1024 * 1024;

        for (int i = 0; i < totalWork; i++)
        {
            msg.putInt(0, i);
            if(writeBuffer.offer(msg) < 0)
            {
                i--;
            }
        }

        long pos = log.getInitialPosition();

        while(logFragmentReader.lastId != totalWork -1)
        {
            long nextPosition = log.pollFragment(pos, logFragmentReader);
            if(nextPosition > 0)
            {
                pos = nextPosition;
            }
        }

        log.closeSync();
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
                .threadingMode(ThreadingMode.DEDICATED)
                .build();
    }

}
