/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.log;

import static io.zeebe.logstreams.log.LogStream.MAX_TOPIC_NAME_LENGTH;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static java.lang.String.join;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.impl.LogStreamBuilder;
import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.*;

public class LogStreamTest
{
    public static final int PARTITION_ID = 0;
    public static final DirectBuffer TOPIC_NAME_BUFFER = BufferUtil.wrapString("default-topic");

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    public TemporaryFolder tempFolder = new TemporaryFolder();
    public AutoCloseableRule closeables = new AutoCloseableRule();
    public ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
    public ServiceContainerRule serviceContainer = new ServiceContainerRule(actorScheduler);

    @Rule
    public RuleChain chain = RuleChain.outerRule(tempFolder)
        .around(actorScheduler)
        .around(serviceContainer)
        .around(closeables);

    protected LogStream buildLogStream(Consumer<LogStreamBuilder> streamConfig)
    {
        final LogStreamBuilder builder = new LogStreamBuilder(TOPIC_NAME_BUFFER, PARTITION_ID);
        builder.logName("test-log-name")
            .serviceContainer(serviceContainer.get())
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .snapshotPeriod(Duration.ofMinutes(5));

        streamConfig.accept(builder);

        return builder.build().join();
    }

    protected LogStream buildLogStream()
    {
        return buildLogStream(c ->
        {
        });
    }

    @Test
    public void shouldFailWithTooLongTopicName()
    {
        // given
        final DirectBuffer topicName = wrapString(join("", Collections.nCopies(MAX_TOPIC_NAME_LENGTH + 1, "f")));

        final LogStreamBuilder builder = new LogStreamBuilder(topicName, PARTITION_ID)
            .logName("some-name")
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .serviceContainer(serviceContainer.get());

        // expect exception
        thrown.expect(RuntimeException.class);
        thrown.expectMessage(String.format("Topic name exceeds max length (%d > %d bytes)", topicName.capacity(), MAX_TOPIC_NAME_LENGTH));

        // when
        builder.build();
    }

    @Test
    public void shouldInitWithoutLogStreamController()
    {
        // when log stream is created
        final LogStream stream = buildLogStream();

        // then log stream contains
        // log storage
        assertNotNull(stream.getLogStorage());
        assertThat(stream.getLogStorage()).isInstanceOf(FsLogStorage.class);

        // block index
        assertNotNull(stream.getLogBlockIndex());

        // and no dispatcher
        assertNull(stream.getWriteBuffer());

        // only log block index controller is created
        assertNotNull(stream.getLogBlockIndexWriter());
        assertNull(stream.getLogStorageAppender());
    }

    @Test
    public void shouldOpenBothController()
    {
        // given log stream
        final LogStream logStream = buildLogStream();

        // when log stream is open

        logStream.openAppender().join();
        closeables.manage(logStream);

        // then
        // log block index is opened and runs now
        assertNotNull(logStream.getLogBlockIndexWriter());

        // log stream controller is opened and runs now
        assertNotNull(logStream.getLogStorageAppender());

        // and logStorage is opened
        assertTrue(logStream.getLogStorage().isOpen());
    }

    @Test
    public void shouldOpenLogBlockIndexControllerOnly()
    {
        // given log stream
        final LogStream logStream = buildLogStream();

        // when log stream is open

        closeables.manage(logStream);

        // then
        // log block index is opened and runs now
        assertNotNull(logStream.getLogBlockIndexWriter());
        assertNotNull(logStream.getLogBlockIndexWriter());

        // log stream controller is null
        assertNull(logStream.getLogStorageAppender());

        // and logStorage is opened
        assertTrue(logStream.getLogStorage().isOpen());
    }

    @Test
    public void shouldStopLogStreamController() throws Exception
    {
        final LogStream logStream = buildLogStream();

        // given open log stream

        logStream.openAppender().join();

        final Dispatcher writeBuffer = logStream.getWriteBuffer();

        // when log streaming is stopped
        logStream.closeAppender().join();

        assertThat(writeBuffer.isClosed()).isTrue();

        // then
        assertNull(logStream.getLogStorageAppender());

        // dispatcher is null as well
        assertNull(logStream.getWriteBuffer());
    }

    @Test
    public void shouldStartLogStreamController()
    {
        // given log stream with without flag
        final LogStream logStream = buildLogStream();

        // when log streaming is started

        logStream.openAppender().join();
        final LogStorageAppender logStreamController = logStream.getLogStorageAppender();

        // then
        // log stream controller has been set and is running
        assertNotNull(logStreamController);

        // dispatcher is initialized
        final Dispatcher writeBuffer = logStream.getWriteBuffer();
        assertNotNull(writeBuffer);
    }

    @Test
    public void shouldLogStorageOnClose()
    {
        // given open log stream
        final LogStream logStream = buildLogStream();

        logStream.openAppender().join();

        // when log stream is closed
        logStream.close();

        // log storage was closed
        assertThat(logStream.getLogStorage().isClosed()).isTrue();
    }

    @Test
    public void shouldThrowExceptionForTruncationWithLogStreamController()
    {
        // given open log stream
        final LogStream logStream = buildLogStream();

        logStream.openAppender().join();
        closeables.manage(logStream);

        // expect exception
        thrown.expect(IllegalArgumentException.class);

        // when truncate is called
        logStream.truncate(0);
    }

    @Test
    public void shouldThrowExceptionForTruncationOfAlreadyCommittedPosition()
    {
        // given
        final int truncatePosition = 101;
        final LogStream logStream = buildLogStream();

        // given open log stream and committed position

        closeables.manage(logStream);

        logStream.setCommitPosition(truncatePosition);

        // expect exception
        thrown.expect(IllegalArgumentException.class);

        // when truncate is called
        logStream.truncate(truncatePosition);
    }

    @Test
    public void shouldTruncateLogStorage()
    {
        // given
        final LogStream logStream = buildLogStream();

        // given open log stream and open block index controller

        logStream.openAppender().join();
        closeables.manage(logStream);

        final LogStreamWriter writer = new LogStreamWriterImpl(logStream);
        final long firstPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();
        final long secondPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();

        waitUntil(() -> events(logStream).count() == 2);
        logStream.getLogStorageAppender().close();

        // when
        logStream.truncate(secondPosition);

        // then
        assertThat(events(logStream).count()).isEqualTo(1);
        assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(firstPosition);
    }

    @Test
    public void shouldWriteNewEventAfterTruncation()
    {
        // given
        final LogStream logStream = buildLogStream();

        // given open log stream and open block index controller

        logStream.openAppender().join();
        closeables.manage(logStream);

        final LogStreamWriter writer = new LogStreamWriterImpl(logStream);
        final long firstPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();

        waitUntil(() -> events(logStream).count() == 1);
        logStream.closeAppender().join();

        logStream.truncate(firstPosition);
        logStream.openAppender().join();

        // when
        final long secondPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();
        waitUntil(() -> events(logStream).count() == 1);

        // then
        assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(secondPosition);
    }

    @Test
    public void shouldTruncateLogStorageWithCommittedPosition()
    {
        // given
        final LogStream logStream = buildLogStream();


        logStream.openAppender().join();
        closeables.manage(logStream);

        final LogStreamWriter writer = new LogStreamWriterImpl(logStream);
        final long firstPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();
        final long secondPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();

        waitUntil(() -> events(logStream).count() == 2);

        logStream.setCommitPosition(firstPosition);

        logStream.getLogStorageAppender().close();

        // when
        logStream.truncate(secondPosition);

        // then
        assertThat(events(logStream).count()).isEqualTo(1);
        assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(firstPosition);
    }

    @Test
    public void shouldTruncateLogStorageForExistingBlockIndexAndCommittedPosition()
    {
        // given
        final LogStream logStream = buildLogStream(b -> b.indexBlockSize(20).readBlockSize(20));

        logStream.openAppender().join();
        closeables.manage(logStream);

        final LogStreamWriter writer = new LogStreamWriterImpl(logStream);
        final long firstPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();
        waitUntil(() -> events(logStream).count() == 1);
        logStream.setCommitPosition(firstPosition);
        logStream.setCommitPosition(firstPosition);
        final long secondPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();
        waitUntil(() -> events(logStream).count() == 2);
        waitUntil(() -> logStream.getLogBlockIndex().size() > 0);

        logStream.getLogStorageAppender().close();

        // when
        logStream.truncate(secondPosition);

        // then
        assertThat(events(logStream).count()).isEqualTo(1);
    }

    /**
     * If position is greater than any logged indexed log entry
     */
    @Test
    public void shouldNotTruncateIfPositionIsGreaterThanCurrentHead()
    {
        // given
        final LogStream logStream = buildLogStream();

        logStream.openAppender().join();
        closeables.manage(logStream);
        logStream.getLogStorageAppender().close();

        final long nonExistingPosition = Long.MAX_VALUE;

        // expect
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Truncation failed! Position " + nonExistingPosition + " was not found.");

        // when truncate is called
        logStream.truncate(nonExistingPosition);
    }

    /**
     * If position does not match a log entry exactly but there is an entry with a higher position
     */
    @Test
    public void shouldTruncateWhenPositionDoesNotMatchEntry()
    {
        // given
        final LogStream logStream = buildLogStream();

        // given open log stream and open block index controller

        logStream.openAppender().join();
        closeables.manage(logStream);

        final LogStreamWriter writer = new LogStreamWriterImpl(logStream);
        final long firstPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();
        final long secondPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();

        waitUntil(() -> events(logStream).count() == 2);
        logStream.getLogStorageAppender().close();

        // when
        logStream.truncate(secondPosition - 1);

        // then
        assertThat(events(logStream).count()).isEqualTo(1);
        assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(firstPosition);
    }

    @Test
    public void shouldNotDeleteOnCloseByDefault()
    {
        final File logDir = tempFolder.getRoot();
        final LogStream logStream = buildLogStream(b -> b.logRootPath(logDir.getAbsolutePath()));

        // when
        logStream.close();

        // then
        final File[] files = logDir.listFiles();
        assertThat(files).isNotNull();
        assertThat(files.length).isGreaterThan(0);
    }

    @Test
    public void shouldDeleteOnClose()
    {
        final File logDir = tempFolder.getRoot();
        final LogStream logStream = buildLogStream(b -> b.logRootPath(logDir.getAbsolutePath()).deleteOnClose(true));

        // when
        logStream.close();

        // then
        final File[] files = logDir.listFiles();
        assertThat(files).isNotNull();
        assertThat(files.length).isEqualTo(0);
    }

    /**
     * including uncommited entries
     */
    protected Stream<LoggedEvent> events(LogStream stream)
    {
        final BufferedLogStreamReader reader = new BufferedLogStreamReader(stream, true);
        closeables.manage(reader);

        reader.seekToFirstEvent();
        final Iterable<LoggedEvent> iterable = () -> reader;
        return StreamSupport.stream(iterable.spliterator(), false);
    }


}
