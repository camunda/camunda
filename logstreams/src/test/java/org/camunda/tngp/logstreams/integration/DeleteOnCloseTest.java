package org.camunda.tngp.logstreams.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.util.buffer.BufferUtil.wrapString;

import java.io.File;
import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.actor.ActorSchedulerImpl;
import org.junit.*;
import org.junit.rules.TemporaryFolder;


public class DeleteOnCloseTest
{
    private static final DirectBuffer TOPIC_NAME = wrapString("test-topic");

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ActorScheduler actorScheduler;

    @Before
    public void setup()
    {
        actorScheduler = ActorSchedulerImpl.createDefaultScheduler();
    }

    @After
    public void destroy() throws Exception
    {
        actorScheduler.close();
    }

    @Test
    public void shouldNotDeleteOnCloseByDefault() throws InterruptedException, ExecutionException
    {
        final File logFolder = tempFolder.getRoot();

        final LogStream log = LogStreams.createFsLogStream(TOPIC_NAME, 0)
            .logRootPath(logFolder.getAbsolutePath())
            .actorScheduler(actorScheduler)
            .build();

        log.open();

        // if
        log.close();

        // then
        assertThat(logFolder.listFiles().length).isGreaterThan(0);
    }

    @Test
    @Ignore
    public void shouldDeleteOnCloseIfSet() throws InterruptedException, ExecutionException
    {
        final File logFolder = tempFolder.getRoot();

        final LogStream log = LogStreams.createFsLogStream(TOPIC_NAME, 0)
            .logRootPath(logFolder.getAbsolutePath())
            .deleteOnClose(true)
            .actorScheduler(actorScheduler)
            .build();

        log.open();

        // if
        log.close();

        // then
        assertThat(logFolder.listFiles().length).isEqualTo(0);
    }
}
