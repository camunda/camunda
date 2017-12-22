/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.task.processor;

import static io.zeebe.protocol.clientapi.EventType.TASK_EVENT;
import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.broker.test.MockStreamProcessorController;
import io.zeebe.broker.test.WrittenEvent;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.time.ClockUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskExpireLockStreamProcessorTest
{
    private static final int STREAM_PROCESSOR_ID = 2;
    private static final DirectBuffer TARGET_LOG_STREAM_TOPIC_NAME = wrapString("test-topic");
    private static final int TARGET_LOG_STREAM_PARTITION_ID = 3;
    private static final long INITIAL_POSITION = 10L;
    private static final int TARGET_LOG_STREAM_TERM = 3;

    private static final byte[] TASK_TYPE = getBytes("test-task");
    private static final DirectBuffer TASK_TYPE_BUFFER = new UnsafeBuffer(TASK_TYPE);

    private static final long LOCK_TIME = ClockUtil.getCurrentTimeInMillis();
    private static final Instant BEFORE_LOCK_TIME = Instant.ofEpochMilli(LOCK_TIME).minusSeconds(60);
    private static final Instant AFTER_LOCK_TIME = Instant.ofEpochMilli(LOCK_TIME).plusSeconds(60);

    private TaskExpireLockStreamProcessor streamProcessor;

    private LogStreamWriter mockLogStreamWriter;
    private LogStreamReader mockLogStreamReader;

    @Mock
    private LogStream mockLogStream;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public MockStreamProcessorController<TaskEvent> mockController = new MockStreamProcessorController<>(TaskEvent.class, event -> event
            .setType(TASK_TYPE_BUFFER)
            .setLockTime(LOCK_TIME)
            .setLockOwner(wrapString("owner")),
            TASK_EVENT,
            INITIAL_POSITION);

    private long loggedEventKey;

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        loggedEventKey = 1L << 32;
        loggedEventKey += 1024;

        MockitoAnnotations.initMocks(this);

        when(mockLogStream.getTopicName()).thenReturn(TARGET_LOG_STREAM_TOPIC_NAME);
        when(mockLogStream.getPartitionId()).thenReturn(TARGET_LOG_STREAM_PARTITION_ID);
        when(mockLogStream.getTerm()).thenReturn(TARGET_LOG_STREAM_TERM);

        streamProcessor = new TaskExpireLockStreamProcessor();

        final StreamProcessorContext streamProcessorContext = new StreamProcessorContext();
        streamProcessorContext.setId(STREAM_PROCESSOR_ID);
        streamProcessorContext.setLogStream(mockLogStream);

        mockController.initStreamProcessor(streamProcessor, streamProcessorContext);
        mockLogStreamReader = streamProcessorContext.getLogStreamReader();
        mockLogStreamWriter = streamProcessorContext.getLogStreamWriter();
    }

    @After
    public void cleanUp()
    {
        ClockUtil.reset();
    }


    @Test
    public void shouldExpireLockIfAfterLockTime()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        final LoggedEvent lockedEvent = mockController.buildLoggedEvent(loggedEventKey, event -> event
                .setState(TaskState.LOCKED));

        mockController.processEvent(lockedEvent);

        when(mockLogStreamReader.seek(INITIAL_POSITION)).thenReturn(true);
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(lockedEvent);

        // when
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();

        final TaskEvent taskEvent = lastWrittenEvent.getValue();
        assertThat(taskEvent.getState()).isEqualTo(TaskState.EXPIRE_LOCK);
        assertThatBuffer(taskEvent.getType()).hasBytes(TASK_TYPE_BUFFER);

        final BrokerEventMetadata metadata = lastWrittenEvent.getMetadata();
        assertThat(metadata.getProtocolVersion()).isEqualTo(Protocol.PROTOCOL_VERSION);
        assertThat(metadata.getEventType()).isEqualTo(TASK_EVENT);

        assertThat(streamProcessor.expirationMap.getBucketBufferArray().getBlockCount()).isEqualTo(0);

        verify(mockLogStreamWriter).key(loggedEventKey);
        verify(mockLogStreamWriter).producerId(STREAM_PROCESSOR_ID);
        verify(mockLogStreamWriter).sourceEvent(TARGET_LOG_STREAM_PARTITION_ID, INITIAL_POSITION);
    }

    @Test
    public void shouldExpireLockIfAfterLockTimeForTwoTasks()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        final LoggedEvent lockedEvent = mockController.buildLoggedEvent(loggedEventKey, event -> event
            .setState(TaskState.LOCKED));
        final LoggedEvent secondLockedEvent = mockController.buildLoggedEvent(loggedEventKey + 1, event -> event
            .setState(TaskState.LOCKED));

        mockController.processEvent(lockedEvent);
        mockController.processEvent(secondLockedEvent);

        when(mockLogStreamReader.seek(INITIAL_POSITION)).thenReturn(true);
        when(mockLogStreamReader.seek(INITIAL_POSITION + 1)).thenReturn(true);
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(lockedEvent, secondLockedEvent);

        // when
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        final List<WrittenEvent<TaskEvent>> writtenEvents = mockController.getWrittenEvents();
        assertThat(writtenEvents.size()).isEqualTo(2);

        final WrittenEvent<TaskEvent> firstEvent = writtenEvents.get(0);
        assertThat(firstEvent.getKey()).isEqualTo(loggedEventKey);

        TaskEvent taskEvent = firstEvent.getValue();
        assertThat(taskEvent.getState()).isEqualTo(TaskState.EXPIRE_LOCK);
        assertThatBuffer(taskEvent.getType()).hasBytes(TASK_TYPE_BUFFER);

        BrokerEventMetadata metadata = firstEvent.getMetadata();
        assertThat(metadata.getProtocolVersion()).isEqualTo(Protocol.PROTOCOL_VERSION);
        assertThat(metadata.getEventType()).isEqualTo(TASK_EVENT);

        final WrittenEvent<TaskEvent> secondEvent = writtenEvents.get(1);
        assertThat(secondEvent.getKey()).isEqualTo(loggedEventKey + 1);

        taskEvent = secondEvent.getValue();
        assertThat(taskEvent.getState()).isEqualTo(TaskState.EXPIRE_LOCK);
        assertThatBuffer(taskEvent.getType()).hasBytes(TASK_TYPE_BUFFER);

        metadata = secondEvent.getMetadata();
        assertThat(metadata.getProtocolVersion()).isEqualTo(Protocol.PROTOCOL_VERSION);
        assertThat(metadata.getEventType()).isEqualTo(TASK_EVENT);

        assertThat(streamProcessor.expirationMap.getBucketBufferArray().getBlockCount()).isEqualTo(0);

        verify(mockLogStreamWriter).key(loggedEventKey);
        verify(mockLogStreamWriter).key(loggedEventKey + 1);
        verify(mockLogStreamWriter, times(2)).producerId(STREAM_PROCESSOR_ID);
        verify(mockLogStreamWriter).sourceEvent(TARGET_LOG_STREAM_PARTITION_ID, INITIAL_POSITION);
        verify(mockLogStreamWriter).sourceEvent(TARGET_LOG_STREAM_PARTITION_ID, INITIAL_POSITION + 1);
    }

    @Test
    public void shouldNotRemoveTaskIfEventWasNotWritten()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        final LoggedEvent lockedEvent = mockController.buildLoggedEvent(loggedEventKey, event -> event
            .setState(TaskState.LOCKED));

        mockController.processEvent(lockedEvent);

        when(mockLogStreamReader.seek(INITIAL_POSITION)).thenReturn(true);
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(lockedEvent);

        // when
        doAnswer(invocationOnMock -> -1).when(mockLogStreamWriter).tryWrite();
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        assertThat(mockController.getWrittenEvents()).isEmpty();

        assertThat(streamProcessor.expirationMap.getBucketBufferArray().getBlockCount()).isEqualTo(1);

        verify(mockLogStreamWriter).key(loggedEventKey);
        verify(mockLogStreamWriter).producerId(STREAM_PROCESSOR_ID);
        verify(mockLogStreamWriter).sourceEvent(TARGET_LOG_STREAM_PARTITION_ID, INITIAL_POSITION);
    }

    @Test
    public void shouldExpireLockOnlyOnce()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        final LoggedEvent lockedEvent = mockController.buildLoggedEvent(2L, event -> event
                .setState(TaskState.LOCKED));

        mockController.processEvent(lockedEvent);

        when(mockLogStreamReader.seek(INITIAL_POSITION)).thenReturn(true);
        when(mockLogStreamReader.hasNext()).thenReturn(true);
        when(mockLogStreamReader.next()).thenReturn(lockedEvent);

        // when
        streamProcessor.checkLockExpirationAsync();
        mockController.drainCommandQueue();

        streamProcessor.checkLockExpirationAsync();
        mockController.drainCommandQueue();

        // then
        assertThat(mockController.getWrittenEvents()).hasSize(1);
    }

    @Test
    public void shouldNotExpireLockIfBeforeLockTime()
    {
        // given
        ClockUtil.setCurrentTime(BEFORE_LOCK_TIME);

        mockController.processEvent(2L, event ->
            event.setState(TaskState.LOCKED));

        // when
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        assertThat(mockController.getWrittenEvents()).isEmpty();
    }

    @Test
    public void shouldNotExpireLockIfCompleted()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        mockController.processEvent(2L, event ->
            event.setState(TaskState.LOCKED));

        mockController.processEvent(2L, event ->
            event.setState(TaskState.COMPLETED));

        // when
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        assertThat(mockController.getWrittenEvents()).isEmpty();
    }

    @Test
    public void shouldNotExpireLockIfMarkedAsFailed()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        mockController.processEvent(2L, event ->
            event.setState(TaskState.LOCKED));

        mockController.processEvent(2L, event ->
            event.setState(TaskState.FAILED));

        // when
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        assertThat(mockController.getWrittenEvents()).isEmpty();
    }

    @Test
    public void shouldNotExpireLockIfAlreadyExpired()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        mockController.processEvent(2L, event ->
            event.setState(TaskState.LOCKED));

        mockController.processEvent(2L, event ->
            event.setState(TaskState.LOCK_EXPIRED));

        // when
        streamProcessor.checkLockExpirationAsync();

        mockController.drainCommandQueue();

        // then
        assertThat(mockController.getWrittenEvents()).isEmpty();
    }

    @Test
    public void shouldFailToExpireLockTimeIfEventNotFound()
    {
        // given
        ClockUtil.setCurrentTime(AFTER_LOCK_TIME);

        mockController.processEvent(2L, event -> event
                .setState(TaskState.LOCKED));

        when(mockLogStreamReader.seek(INITIAL_POSITION)).thenReturn(false);
        when(mockLogStreamReader.hasNext()).thenReturn(false);

        // when
        streamProcessor.checkLockExpirationAsync();

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Failed to check the task lock expiration time");

        mockController.drainCommandQueue();
    }

}
