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
import static io.zeebe.logstreams.log.LogTestUtil.PARTITION_ID;
import static io.zeebe.logstreams.log.LogTestUtil.TOPIC_NAME_BUFFER;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static java.lang.String.join;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.fs.FsLogStreamBuilder;
import io.zeebe.logstreams.impl.LogStreamController;
import io.zeebe.logstreams.impl.LogStreamImpl;
import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;

public class LogStreamTest
{

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    public TemporaryFolder tempFolder = new TemporaryFolder();
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public RuleChain chain = RuleChain.outerRule(tempFolder).around(closeables);

    private ActorScheduler actorScheduler;

    @Before
    public void setUp()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("foo");
        closeables.manage(actorScheduler);
    }

    protected LogStream buildLogStream(Consumer<FsLogStreamBuilder> streamConfig)
    {
        final FsLogStreamBuilder builder = new FsLogStreamBuilder(TOPIC_NAME_BUFFER, PARTITION_ID);
        builder.actorScheduler(actorScheduler)
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .snapshotPolicy(pos -> false);

        streamConfig.accept(builder);

        return builder.build();
    }

    protected LogStream buildLogStream()
    {
        return buildLogStream(c ->
        {
        });
    }

    @Test
    public void shouldFailWithToLongTopicName()
    {
        // given
        final DirectBuffer topicName = wrapString(join("", Collections.nCopies(MAX_TOPIC_NAME_LENGTH + 1, "f")));

        final FsLogStreamBuilder builder = new FsLogStreamBuilder(topicName, PARTITION_ID)
                .logRootPath(tempFolder.getRoot().getAbsolutePath())
                .actorScheduler(actorScheduler);

        // expect exception
        thrown.expect(RuntimeException.class);
        thrown.expectMessage(String.format("Topic name exceeds max length (%d > %d bytes)", topicName.capacity(), MAX_TOPIC_NAME_LENGTH));

        // when
        builder.build();
    }

    @Test
    public void shouldInitWithoutLogStreamController()
    {
        // when log stream is created with builder and without flag is set
        final LogStream stream = buildLogStream(b -> b.logStreamControllerDisabled(true));

        // then log stream contains
        // log storage
        assertNotNull(stream.getLogStorage());
        assertThat(stream.getLogStorage()).isInstanceOf(FsLogStorage.class);

        // block index
        assertNotNull(stream.getLogBlockIndex());

        // and no dispatcher
        assertNull(stream.getWriteBuffer());

        // only log block index controller is created
        assertNotNull(stream.getLogBlockIndexController());
        assertNull(stream.getLogStreamController());
    }

    @Test
    public void shouldOpenBothController()
    {
        // given log stream
        final LogStream logStream = buildLogStream();

        // when log stream is open
        logStream.open();
        closeables.manage(logStream);

        // then
        // log block index is opened and runs now
        assertTrue(logStream.getLogBlockIndexController().isRunning());

        // log stream controller is opened and runs now
        assertTrue(logStream.getLogStreamController().isRunning());

        // and logStorage is opened
        assertTrue(logStream.getLogStorage().isOpen());
    }

    @Test
    public void shouldOpenLogBlockIndexControllerOnly()
    {
        // given log stream with without flag
        final LogStream logStream = buildLogStream(b -> b.logStreamControllerDisabled(true));

        // when log stream is open
        logStream.open();
        closeables.manage(logStream);

        // then
        // log block index is opened and runs now
        assertTrue(logStream.getLogBlockIndexController().isRunning());

        // log stream controller is null
        assertNull(logStream.getLogStreamController());

        // and logStorage is opened
        assertTrue(logStream.getLogStorage().isOpen());
    }

    @Test
    public void shouldStopLogStreamController()
    {
        final LogStream logStream = buildLogStream();

        // given open log stream
        logStream.open();
        final Dispatcher writeBuffer = logStream.getWriteBuffer();

        // when log streaming is stopped
        logStream.closeLogStreamController().join();

        assertThat(writeBuffer.isClosed()).isTrue();

        // then
        // log stream controller has stop running and reference is not null for reuse
        assertNotNull(logStream.getLogStreamController());
        assertFalse(logStream.getLogStreamController().isRunning());

        // dispatcher is null as well
        assertNull(logStream.getWriteBuffer());
    }

    @Test
    public void shouldStopAndStartLogStreamController()
    {
        // given
        final LogStream logStream = buildLogStream();

        // given open log stream with stopped log stream controller
        logStream.open();
        final LogStreamController controller1 = logStream.getLogStreamController();

        logStream.closeLogStreamController().join();

        // when log streaming is started
        logStream.openLogStreamController().join();
        final LogStreamController controller2 = logStream.getLogStreamController();

        // then
        // log stream controller is reused
        assertEquals(controller2, controller1);
        assertNotNull(controller2);

        // is running
        assertTrue(controller2.isRunning());

        // dispatcher is initialized
        assertNotNull(logStream.getWriteBuffer());
    }

    @Test
    public void shouldStopAndStartLogStreamControllerWithDifferentAgentRunners()
    {
        // given
        final LogStream logStream = buildLogStream();

        // set up block index and log storage
        final ActorScheduler scheduler2 = ActorSchedulerBuilder.createDefaultScheduler("bar");

        // given open log stream with stopped log stream controller
        logStream.open();
        closeables.manage(logStream);
        logStream.closeLogStreamController().join();

        // when log streaming is started
        logStream.openLogStreamController(scheduler2).join();
        final LogStreamController logStreamController2 = logStream.getLogStreamController();

        // then
        // log stream controller has been set and is running
        assertNotNull(logStreamController2);
        assertTrue(logStreamController2.isRunning());

        // dispatcher is initialized
        assertNotNull(logStream.getWriteBuffer());
    }

    @Test
    public void shouldStartLogStreamController()
    {
        // given log stream with without flag
        final LogStream logStream = buildLogStream(b -> b.logStreamControllerDisabled(true));

        // when log streaming is started
        logStream.openLogStreamController(actorScheduler);
        final LogStreamController logStreamController = logStream.getLogStreamController();

        // then
        // log stream controller has been set and is running
        assertNotNull(logStreamController);
        assertTrue(logStreamController.isRunning());

        // dispatcher is initialized
        final Dispatcher writeBuffer = logStream.getWriteBuffer();
        assertNotNull(writeBuffer);
    }

    @Test
    public void shouldCloseBothControllers()
    {
        // given open log stream
        final LogStream logStream = buildLogStream();

        logStream.open();

        // when log stream is closed
        logStream.close();

        // then controllers are not running
        assertFalse(logStream.getLogBlockIndexController().isRunning());
        assertFalse(logStream.getLogStreamController().isRunning());

        // and log storage was closed
        assertThat(logStream.getLogStorage().isClosed()).isTrue();
    }

    @Test
    public void shouldCloseLogStreamControllerAndAfterwardsStream()
    {
        // given open log stream
        final LogStream logStream = buildLogStream();
        logStream.open();

        // when log stream controller is closed
        logStream.closeLogStreamController().join();

        // then the log stream is closed
        assertThat(logStream.getWriteBuffer()).isNull();
        assertThat(logStream.getLogStreamController().isRunning()).isFalse();

        // when log stream is closed
        logStream.closeAsync().join();

        // then
        assertThat(logStream.getLogBlockIndexController().isClosed()).isTrue();
    }

    @Test
    public void shouldCloseLogBlockIndexController()
    {
        // given open log stream without log stream controller
        final LogStream logStream = buildLogStream(b -> b.logStreamControllerDisabled(true));
        logStream.open();

        // when log stream is closed
        logStream.closeAsync().join();

        // then
        // controllers are not running
        assertFalse(logStream.getLogBlockIndexController().isRunning());

        // and log storage was closed
        assertThat(logStream.getLogStorage().isClosed()).isTrue();
    }

    @Test
    public void shouldOpenBothClosedController()
    {
        // given open->close log stream
        final LogStream logStream = buildLogStream();
        closeables.manage(logStream);

        logStream.open();
        logStream.close();

        // when open log stream again
        logStream.open();

        // then controllers run again
        assertTrue(logStream.getLogBlockIndexController().isRunning());
        assertTrue(logStream.getLogStreamController().isRunning());
    }

    @Test
    public void shouldOpenClosedLogBlockIndexController()
    {
        // given open->close log stream without log stream controller
        final LogStream logStream = buildLogStream(b -> b.logStreamControllerDisabled(true));
        logStream.open();
        closeables.manage(logStream);

        logStream.close();

        // when
        logStream.open();

        // then
        assertThat(logStream.getLogBlockIndexController().isRunning()).isTrue();
    }

    @Test
    public void shouldThrowExceptionForTruncationWithLogStreamController()
    {
        // given open log stream
        final LogStream logStream = buildLogStream();

        logStream.open();
        closeables.manage(logStream);

        // expect exception
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(LogStreamImpl.EXCEPTION_MSG_TRUNCATE_AND_LOG_STREAM_CTRL_IN_PARALLEL);

        // when truncate is called
        logStream.truncate(0);
    }

    @Test
    public void shouldThrowExceptionForTruncationOfAlreadyCommittedPosition()
    {
        // given
        final int truncatePosition = 101;
        final LogStream logStream = buildLogStream(b -> b.logStreamControllerDisabled(true));

        // given open log stream and committed position
        logStream.open();
        closeables.manage(logStream);

        logStream.setCommitPosition(truncatePosition);

        // expect exception
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(LogStreamImpl.EXCEPTION_MSG_TRUNCATE_COMMITTED_POSITION);

        // when truncate is called
        logStream.truncate(truncatePosition);
    }

    @Test
    public void shouldTruncateLogStorage()
    {
        // given
        final LogStream logStream = buildLogStream();

        // given open log stream and open block index controller
        logStream.open();
        closeables.manage(logStream);

        final LogStreamWriter writer = new LogStreamWriterImpl(logStream);
        final long firstPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();
        final long secondPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();

        waitUntil(() -> events(logStream).count() == 2);
        logStream.getLogStreamController().close();

        // when
        logStream.truncate(secondPosition);

        // then
        ensureLogStreamCanBeClosed(logStream);
        assertThat(events(logStream).count()).isEqualTo(1);
        assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(firstPosition);
    }

    protected void ensureLogStreamCanBeClosed(LogStream stream)
    {
        // closing the log stream successfully only works when the stream controller is open...
        // TODO: fix this properly by making the log stream close method tolerate these cases
        stream.getLogStreamController().open();
    }

    @Test
    public void shouldWriteNewEventAfterTruncation()
    {
        // given
        final LogStream logStream = buildLogStream();

        // given open log stream and open block index controller
        logStream.open();
        closeables.manage(logStream);

        final LogStreamWriter writer = new LogStreamWriterImpl(logStream);
        final long firstPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();

        waitUntil(() -> events(logStream).count() == 1);
        logStream.getLogStreamController().close();

        logStream.truncate(firstPosition);
        logStream.getLogStreamController().open();

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

        logStream.open();
        closeables.manage(logStream);

        final LogStreamWriter writer = new LogStreamWriterImpl(logStream);
        final long firstPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();
        final long secondPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();

        waitUntil(() -> events(logStream).count() == 2);

        logStream.setCommitPosition(firstPosition);

        logStream.getLogStreamController().close();

        // when
        logStream.truncate(secondPosition);

        // then
        ensureLogStreamCanBeClosed(logStream);
        assertThat(events(logStream).count()).isEqualTo(1);
        assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(firstPosition);
    }

    @Test
    public void shouldTruncateLogStorageForExistingBlockIndexAndCommittedPosition()
    {
        // given
        final LogStream logStream = buildLogStream(b -> b.indexBlockSize(20));

        logStream.open();
        closeables.manage(logStream);

        final LogStreamWriter writer = new LogStreamWriterImpl(logStream);
        final long firstPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();
        waitUntil(() -> events(logStream).count() == 1);

        logStream.setCommitPosition(firstPosition);
        waitUntil(() -> logStream.getLogBlockIndex().size() > 0);

        final long secondPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();
        waitUntil(() -> events(logStream).count() == 2);

        logStream.getLogStreamController().close();

        // when
        logStream.truncate(secondPosition);

        // then
        ensureLogStreamCanBeClosed(logStream);
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
        logStream.open();
        closeables.manage(logStream);
        logStream.getLogStreamController().close();

        final long nonExistingPosition = Long.MAX_VALUE;

        // expect
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Truncation failed! Position " + nonExistingPosition + " was not found.");

        // when truncate is called
        try
        {
            logStream.truncate(nonExistingPosition);
        }
        finally
        {
            ensureLogStreamCanBeClosed(logStream);
        }
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
        logStream.open();
        closeables.manage(logStream);

        final LogStreamWriter writer = new LogStreamWriterImpl(logStream);
        final long firstPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();
        final long secondPosition = writer.key(123L).value(new UnsafeBuffer(new byte[4])).tryWrite();

        waitUntil(() -> events(logStream).count() == 2);
        logStream.getLogStreamController().close();

        // when
        logStream.truncate(secondPosition - 1);

        // then
        ensureLogStreamCanBeClosed(logStream);
        assertThat(events(logStream).count()).isEqualTo(1);
        assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(firstPosition);
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
