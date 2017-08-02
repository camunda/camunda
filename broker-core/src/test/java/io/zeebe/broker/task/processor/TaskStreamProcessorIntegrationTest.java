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

import static io.zeebe.broker.test.MsgPackUtil.*;
import static io.zeebe.protocol.clientapi.EventType.TASK_EVENT;
import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.broker.transport.clientapi.SubscribedEventWriter;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.test.util.FluentMock;
import io.zeebe.test.util.agent.ControllableTaskScheduler;
import io.zeebe.util.time.ClockUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskStreamProcessorIntegrationTest
{
    private static final DirectBuffer TOPIC_NAME = wrapString("test-topic");
    private static final int PARTITION_ID = 1;

    private static final byte[] TASK_TYPE = getBytes("test-task");

    private static final DirectBuffer TASK_TYPE_BUFFER = new UnsafeBuffer(TASK_TYPE);

    private LogStream logStream;

    private LockTaskStreamProcessor lockTaskStreamProcessor;
    private TaskExpireLockStreamProcessor taskExpireLockStreamProcessor;

    private StreamProcessorController taskInstanceStreamProcessorController;
    private StreamProcessorController taskSubscriptionStreamProcessorController;
    private StreamProcessorController taskExpireLockStreamProcessorController;

    @Rule
    public ControllableTaskScheduler taskScheduler = new ControllableTaskScheduler();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public Timeout testTimeout = Timeout.seconds(5);

    @FluentMock
    private CommandResponseWriter mockResponseWriter;

    @FluentMock
    private SubscribedEventWriter mockSubscribedEventWriter;

    @Mock
    private TaskSubscriptionManager mockTaskSubscriptionManager;

    private LogStreamWriter logStreamWriter;

    private final BrokerEventMetadata defaultBrokerEventMetadata = new BrokerEventMetadata();

    private final TaskEvent followUpTaskEvent = new TaskEvent();
    private BufferedLogStreamReader logStreamReader;

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        MockitoAnnotations.initMocks(this);

        when(mockResponseWriter.tryWriteResponse(anyInt(), anyLong())).thenReturn(true);
        when(mockSubscribedEventWriter.tryWriteMessage(anyInt())).thenReturn(true);

        final String rootPath = tempFolder.getRoot().getAbsolutePath();

        logStream = LogStreams.createFsLogStream(TOPIC_NAME, PARTITION_ID)
            .logRootPath(rootPath)
            .actorScheduler(taskScheduler)
            .deleteOnClose(true)
            .build();

        logStream.openAsync();

        final SnapshotStorage snapshotStorage = LogStreams.createFsSnapshotStore(rootPath).build();

        final StreamProcessor taskInstanceStreamProcessor = new TaskInstanceStreamProcessor(mockResponseWriter, mockSubscribedEventWriter, mockTaskSubscriptionManager);
        taskInstanceStreamProcessorController = LogStreams.createStreamProcessor("task-instance", 0, taskInstanceStreamProcessor)
            .sourceStream(logStream)
            .targetStream(logStream)
            .snapshotStorage(snapshotStorage)
            .actorScheduler(taskScheduler)
            .build();

        lockTaskStreamProcessor = new LockTaskStreamProcessor(TASK_TYPE_BUFFER);
        taskSubscriptionStreamProcessorController = LogStreams.createStreamProcessor("task-lock", 1, lockTaskStreamProcessor)
            .sourceStream(logStream)
            .targetStream(logStream)
            .snapshotStorage(snapshotStorage)
            .actorScheduler(taskScheduler)
            .build();

        taskExpireLockStreamProcessor = new TaskExpireLockStreamProcessor();
        taskExpireLockStreamProcessorController = LogStreams.createStreamProcessor("task-expire-lock", 2, taskExpireLockStreamProcessor)
                .sourceStream(logStream)
                .targetStream(logStream)
                .snapshotStorage(snapshotStorage)
                .actorScheduler(taskScheduler)
                .build();

        taskInstanceStreamProcessorController.openAsync();
        taskSubscriptionStreamProcessorController.openAsync();
        taskExpireLockStreamProcessorController.openAsync();

        logStreamReader = new BufferedLogStreamReader(logStream, true);

        logStreamWriter = new LogStreamWriterImpl(logStream);
        defaultBrokerEventMetadata.eventType(TASK_EVENT);

        taskScheduler.waitUntilDone();
    }

    @After
    public void cleanUp() throws Exception
    {
        taskInstanceStreamProcessorController.closeAsync();
        taskSubscriptionStreamProcessorController.closeAsync();
        taskExpireLockStreamProcessorController.closeAsync();

        logStreamReader.close();

        logStream.closeAsync();

        ClockUtil.reset();
    }

    @Test
    public void shouldCreateTask() throws InterruptedException, ExecutionException
    {
        // given
        final TaskEvent taskEvent = new TaskEvent()
            .setState(TaskState.CREATE)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setPayload(new UnsafeBuffer(MSGPACK_PAYLOAD));

        // when
        final long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        // then
        assertThatEventIsFollowedBy(position, TaskState.CREATED);

        assertThatBuffer(followUpTaskEvent.getType()).hasBytes(TASK_TYPE_BUFFER.byteArray());

        assertThatBuffer(followUpTaskEvent.getPayload())
            .hasCapacity(MSGPACK_PAYLOAD.length)
            .hasBytes(MSGPACK_PAYLOAD);

        verify(mockResponseWriter).topicName(TOPIC_NAME);
        verify(mockResponseWriter).partitionId(PARTITION_ID);
        verify(mockResponseWriter).key(2L);
        verify(mockResponseWriter).tryWriteResponse(anyInt(), anyLong());
    }

    @Test
    public void shouldLockTask() throws InterruptedException, ExecutionException
    {
        // given
        lockTaskStreamProcessor.addSubscription(createTaskSubscription());

        final TaskEvent taskEvent = new TaskEvent()
            .setState(TaskState.CREATE)
            .setRetries(3)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setPayload(new UnsafeBuffer(MSGPACK_PAYLOAD));

        // when
        final long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        // then
        LoggedEvent event = assertThatEventIsFollowedBy(position, TaskState.CREATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        assertThat(followUpTaskEvent.getLockTime()).isGreaterThan(0);
        assertThat(followUpTaskEvent.getLockOwner()).isEqualTo(wrapString("owner"));

        assertThatBuffer(followUpTaskEvent.getPayload())
            .hasCapacity(MSGPACK_PAYLOAD.length)
            .hasBytes(MSGPACK_PAYLOAD);

        verify(mockResponseWriter, times(1)).tryWriteResponse(anyInt(), anyLong());

        verify(mockSubscribedEventWriter, times(1)).tryWriteMessage(11);
        verify(mockSubscribedEventWriter).subscriberKey(1L);
        verify(mockSubscribedEventWriter).subscriptionType(SubscriptionType.TASK_SUBSCRIPTION);
    }

    @Test
    public void shouldCompleteTask() throws Exception
    {
        // given
        lockTaskStreamProcessor.addSubscription(createTaskSubscription());

        final TaskEvent taskEvent = new TaskEvent()
            .setState(TaskState.CREATE)
            .setRetries(3)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setPayload(new UnsafeBuffer(MSGPACK_PAYLOAD));

        long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        LoggedEvent event = assertThatEventIsFollowedBy(position, TaskState.CREATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        // when
        final byte[] modifiedPayload = MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}"));

        taskEvent
            .setState(TaskState.COMPLETE)
            .setLockOwner(wrapString("owner"))
            .setPayload(new UnsafeBuffer(modifiedPayload));

        position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        // then
        event = assertThatEventIsFollowedBy(position, TaskState.COMPLETED);
        logStream.setCommitPosition(event.getPosition());

        assertThatBuffer(followUpTaskEvent.getPayload())
            .hasCapacity(modifiedPayload.length)
            .hasBytes(modifiedPayload);

        verify(mockResponseWriter, times(2)).tryWriteResponse(anyInt(), anyLong());
    }

    @Test
    public void shouldFailTask() throws InterruptedException, ExecutionException
    {
        // given
        lockTaskStreamProcessor.addSubscription(createTaskSubscription());

        final TaskEvent taskEvent = new TaskEvent()
            .setState(TaskState.CREATE)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setRetries(3)
            .setPayload(new UnsafeBuffer(MSGPACK_PAYLOAD));

        long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        LoggedEvent event = assertThatEventIsFollowedBy(position, TaskState.CREATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        // when
        taskEvent
            .setState(TaskState.FAIL)
            .setLockOwner(wrapString("owner"))
            .setRetries(2);

        position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        // then
        event = assertThatEventIsFollowedBy(position, TaskState.FAILED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        verify(mockResponseWriter, times(2)).tryWriteResponse(anyInt(), anyLong());
    }

    @Test
    public void shouldExpireTaskLock() throws InterruptedException, ExecutionException
    {
        // given
        final Instant now = Instant.now();
        final Duration lockDuration = Duration.ofMinutes(5);

        ClockUtil.setCurrentTime(now);

        lockTaskStreamProcessor.addSubscription(createTaskSubscription());

        final TaskEvent taskEvent = new TaskEvent()
            .setState(TaskState.CREATE)
            .setRetries(3)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setPayload(new UnsafeBuffer(MSGPACK_PAYLOAD));

        final long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        LoggedEvent event = assertThatEventIsFollowedBy(position, TaskState.CREATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        taskScheduler.waitUntilDone();

        // when
        ClockUtil.setCurrentTime(now.plus(lockDuration));

        taskExpireLockStreamProcessor.checkLockExpirationAsync();

        // then
        event = assertThatEventIsFollowedBy(event, TaskState.EXPIRE_LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCK_EXPIRED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        assertThat(followUpTaskEvent.getLockTime()).isGreaterThan(now.plus(lockDuration).toEpochMilli());
        assertThat(followUpTaskEvent.getLockOwner()).isEqualTo(wrapString("owner"));

        assertThatBuffer(followUpTaskEvent.getPayload())
            .hasCapacity(MSGPACK_PAYLOAD.length)
            .hasBytes(MSGPACK_PAYLOAD);

        verify(mockResponseWriter, times(1)).tryWriteResponse(anyInt(), anyLong());
        verify(mockSubscribedEventWriter, times(2)).tryWriteMessage(anyInt());
    }

    @Test
    public void shouldUpdateTaskRetries() throws InterruptedException, ExecutionException
    {
        // given
        lockTaskStreamProcessor.addSubscription(createTaskSubscription());

        final TaskEvent taskEvent = new TaskEvent()
            .setState(TaskState.CREATE)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setRetries(1)
            .setPayload(new UnsafeBuffer(MSGPACK_PAYLOAD));

        long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        LoggedEvent event = assertThatEventIsFollowedBy(position, TaskState.CREATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        taskEvent
            .setState(TaskState.FAIL)
            .setLockOwner(wrapString("owner"))
            .setRetries(0);

        position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        event = assertThatEventIsFollowedBy(position, TaskState.FAILED);
        logStream.setCommitPosition(event.getPosition());

        // when
        taskEvent
            .setState(TaskState.UPDATE_RETRIES)
            .setRetries(2);

        position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        // then
        event = assertThatEventIsFollowedBy(position, TaskState.RETRIES_UPDATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskState.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        verify(mockResponseWriter, times(3)).tryWriteResponse(anyInt(), anyLong());

        assertThat(followUpTaskEvent.getRetries()).isEqualTo(2);
    }

    private LoggedEvent assertThatEventIsFollowedBy(LoggedEvent event, TaskState eventType)
    {
        return assertThatEventIsFollowedBy(event.getPosition(), eventType);
    }

    private LoggedEvent assertThatEventIsFollowedBy(long position, TaskState eventType)
    {
        final LoggedEvent event = getResultEventOf(position);

        followUpTaskEvent.reset();
        event.readValue(followUpTaskEvent);

        assertThat(followUpTaskEvent.getState()).isEqualTo(eventType);

        return event;
    }

    private LoggedEvent getResultEventOf(long position)
    {
        taskScheduler.waitUntilDone();

        LoggedEvent loggedEvent = null;
        long sourceEventPosition = -1;

        while (sourceEventPosition < position)
        {
            if (logStreamReader.hasNext())
            {
                loggedEvent = logStreamReader.next();
                sourceEventPosition = loggedEvent.getSourceEventPosition();
            }
        }

        assertThat(sourceEventPosition).isEqualTo(position);

        return loggedEvent;
    }

    private TaskSubscription createTaskSubscription()
    {
        final TaskSubscription subscription = new TaskSubscription(wrapString("topic"), 0, TASK_TYPE_BUFFER, Duration.ofMinutes(5).toMillis(), wrapString("owner"), 11);
        subscription.setSubscriberKey(1L);
        subscription.setCredits(10);

        return subscription;
    }

}
