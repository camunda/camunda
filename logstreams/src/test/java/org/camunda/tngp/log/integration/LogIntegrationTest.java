package org.camunda.tngp.log.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.log.BufferedLogReader;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogEntryWriter;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.Logs;
import org.camunda.tngp.log.ReadableLogEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LogIntegrationTest
{
    private static final int MSG_SIZE = 911;

    @Rule
    public TemporaryFolder temFolder = new TemporaryFolder();

    @Test
    public void shouldAppend() throws InterruptedException, ExecutionException
    {
        final String logPath = temFolder.getRoot().getAbsolutePath();

        final Log log = Logs.createFsLog("foo", 0)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 8)
                .build()
                .get();

        final LogEntryWriter writer = new LogEntryWriter(log);
        final LogReader logReader = new BufferedLogReader(log);

        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(MSG_SIZE));

        for (int j = 0; j < 50; j++)
        {
            final int workPerIteration = 25000;

            for (int i = 0; i < workPerIteration; i++)
            {
                msg.putInt(0, i);

                writer
                    .key(i)
                    .value(msg);

                while (writer.tryWrite() < 0)
                {
                    // spin
                }
            }


            int count = 0;
            long lastPosition = -1L;

            while (count < workPerIteration)
            {
                if (logReader.hasNext())
                {
                    final ReadableLogEntry entry = logReader.next();
                    final long currentPosition = entry.getPosition();

                    assertThat(currentPosition > lastPosition);

                    final DirectBuffer valueBuffer = entry.getValueBuffer();
                    final long value = valueBuffer.getInt(entry.getValueOffset());
                    assertThat(value).isEqualTo(entry.getLongKey());
                    assertThat(entry.getValueLength()).isEqualTo(MSG_SIZE);

                    lastPosition = currentPosition;

                    count++;
                }
            }

            assertThat(count).isEqualTo(workPerIteration);
            assertThat(logReader.hasNext()).isFalse();
        }

        log.close();
    }

}
