package org.camunda.tngp.log;

import java.io.File;
import java.nio.ByteBuffer;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.LogBuilder.ThreadingMode;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TestLogAppender
{

    @Test
    public void shouldAppend()
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
            .writeBufferSize(1024 * 1024 * 1024)
            .threadingMode(ThreadingMode.DEDICATED)
            .build();

        final Dispatcher writeBuffer = log.getWriteBuffer();

        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

        for(int i = 0; i < 1024 * 1024 * 10; i++)
        {
            while(writeBuffer.offer(msg) < 0)
            {
                // spin
            }
        }
    }
}
