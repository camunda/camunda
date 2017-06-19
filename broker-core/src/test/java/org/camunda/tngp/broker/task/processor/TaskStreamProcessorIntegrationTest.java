package org.camunda.tngp.broker.task.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.JSON_MAPPER;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.MSGPACK_MAPPER;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.MSGPACK_PAYLOAD;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;
import static org.camunda.tngp.test.util.BufferAssert.assertThatBuffer;
import static org.camunda.tngp.util.StringUtil.getBytes;
import static org.camunda.tngp.util.buffer.BufferUtil.wrapString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.task.TaskSubscriptionManager;
import org.camunda.tngp.broker.task.data.TaskEvent;
import org.camunda.tngp.broker.task.data.TaskEventType;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.*;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.test.util.FluentMock;
import org.camunda.tngp.test.util.agent.ControllableAgentRunnerService;
import org.camunda.tngp.util.time.ClockUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
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
    public ControllableAgentRunnerService agentRunnerService = new ControllableAgentRunnerService();

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
    private final BrokerEventMetadata lastBrokerEventMetadata = new BrokerEventMetadata();

    private final TaskEvent followUpTaskEvent = new TaskEvent();

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        MockitoAnnotations.initMocks(this);

        when(mockResponseWriter.tryWriteResponse()).thenReturn(true);
        when(mockSubscribedEventWriter.tryWriteMessage()).thenReturn(true);

        doAnswer(invocation ->
        {
            final BrokerEventMetadata metadata =  (BrokerEventMetadata) invocation.getArguments()[0];

            final UnsafeBuffer metadataBuffer = new UnsafeBuffer(new byte[metadata.getLength()]);
            metadata.write(metadataBuffer, 0);

            lastBrokerEventMetadata.wrap(metadataBuffer, 0, metadataBuffer.capacity());

            return invocation.getMock();
        }).when(mockResponseWriter).brokerEventMetadata(any(BrokerEventMetadata.class));

        final String rootPath = tempFolder.getRoot().getAbsolutePath();

        logStream = LogStreams.createFsLogStream(TOPIC_NAME, PARTITION_ID)
            .logRootPath(rootPath)
            .agentRunnerService(agentRunnerService)
            .writeBufferAgentRunnerService(agentRunnerService)
            .deleteOnClose(true)
            .build();

        logStream.openAsync();

        final SnapshotStorage snapshotStorage = LogStreams.createFsSnapshotStore(rootPath).build();
        final FileChannelIndexStore indexStore = FileChannelIndexStore.tempFileIndexStore();

        final StreamProcessor taskInstanceStreamProcessor = new TaskInstanceStreamProcessor(mockResponseWriter, mockSubscribedEventWriter, indexStore, mockTaskSubscriptionManager);
        taskInstanceStreamProcessorController = LogStreams.createStreamProcessor("task-instance", 0, taskInstanceStreamProcessor)
            .sourceStream(logStream)
            .targetStream(logStream)
            .snapshotStorage(snapshotStorage)
            .agentRunnerService(agentRunnerService)
            .build();

        lockTaskStreamProcessor = new LockTaskStreamProcessor(TASK_TYPE_BUFFER);
        taskSubscriptionStreamProcessorController = LogStreams.createStreamProcessor("task-lock", 1, lockTaskStreamProcessor)
            .sourceStream(logStream)
            .targetStream(logStream)
            .snapshotStorage(snapshotStorage)
            .agentRunnerService(agentRunnerService)
            .build();

        taskExpireLockStreamProcessor = new TaskExpireLockStreamProcessor();
        taskExpireLockStreamProcessorController = LogStreams.createStreamProcessor("task-expire-lock", 2, taskExpireLockStreamProcessor)
                .sourceStream(logStream)
                .targetStream(logStream)
                .snapshotStorage(snapshotStorage)
                .agentRunnerService(agentRunnerService)
                .build();

        taskInstanceStreamProcessorController.openAsync();
        taskSubscriptionStreamProcessorController.openAsync();
        taskExpireLockStreamProcessorController.openAsync();

        logStreamWriter = new LogStreamWriterImpl(logStream);
        defaultBrokerEventMetadata.eventType(TASK_EVENT);

        agentRunnerService.waitUntilDone();
    }

    @After
    public void cleanUp() throws Exception
    {
        taskInstanceStreamProcessorController.closeAsync();
        taskSubscriptionStreamProcessorController.closeAsync();
        taskExpireLockStreamProcessorController.closeAsync();

        logStream.closeAsync();

        ClockUtil.reset();
    }

    @Test
    public void shouldCreateTask() throws InterruptedException, ExecutionException
    {
        // given
        final TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
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
        assertThatEventIsFollowedBy(position, TaskEventType.CREATED);

        assertThatBuffer(followUpTaskEvent.getType()).hasBytes(TASK_TYPE_BUFFER.byteArray());

        assertThatBuffer(followUpTaskEvent.getPayload())
            .hasCapacity(MSGPACK_PAYLOAD.length)
            .hasBytes(MSGPACK_PAYLOAD);

        verify(mockResponseWriter).topicName(TOPIC_NAME);
        verify(mockResponseWriter).partitionId(PARTITION_ID);
        verify(mockResponseWriter).key(2L);
        verify(mockResponseWriter).tryWriteResponse();
    }

    @Test
    public void shouldLockTask() throws InterruptedException, ExecutionException
    {
        // given
        final TaskSubscription subscription = new TaskSubscription()
                .setSubscriberKey(1L)
                .setChannelId(11)
                .setTaskType(TASK_TYPE_BUFFER)
                .setLockDuration(Duration.ofMinutes(5).toMillis())
                .setLockOwner(wrapString("owner"))
                .setCredits(10);

        lockTaskStreamProcessor.addSubscription(subscription);

        final TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
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
        LoggedEvent event = assertThatEventIsFollowedBy(position, TaskEventType.CREATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        assertThat(followUpTaskEvent.getLockTime()).isGreaterThan(0);
        assertThat(followUpTaskEvent.getLockOwner()).isEqualTo(wrapString("owner"));

        assertThatBuffer(followUpTaskEvent.getPayload())
            .hasCapacity(MSGPACK_PAYLOAD.length)
            .hasBytes(MSGPACK_PAYLOAD);

        verify(mockResponseWriter, times(1)).tryWriteResponse();

        verify(mockSubscribedEventWriter, times(1)).tryWriteMessage();
        verify(mockSubscribedEventWriter).channelId(11);
        verify(mockSubscribedEventWriter).subscriberKey(1L);
        verify(mockSubscribedEventWriter).subscriptionType(SubscriptionType.TASK_SUBSCRIPTION);
    }

    @Test
    public void shouldCompleteTask() throws Exception
    {
        // given
        final TaskSubscription subscription = new TaskSubscription()
                .setSubscriberKey(1L)
                .setChannelId(11)
                .setTaskType(TASK_TYPE_BUFFER)
                .setLockDuration(Duration.ofMinutes(5).toMillis())
                .setLockOwner(wrapString("owner"))
                .setCredits(10);

        lockTaskStreamProcessor.addSubscription(subscription);

        final TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
            .setRetries(3)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setPayload(new UnsafeBuffer(MSGPACK_PAYLOAD));

        long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        LoggedEvent event = assertThatEventIsFollowedBy(position, TaskEventType.CREATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        // when
        final byte[] modifiedPayload = MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}"));

        taskEvent
            .setEventType(TaskEventType.COMPLETE)
            .setLockOwner(wrapString("owner"))
            .setPayload(new UnsafeBuffer(modifiedPayload));

        position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        // then
        event = assertThatEventIsFollowedBy(position, TaskEventType.COMPLETED);
        logStream.setCommitPosition(event.getPosition());

        assertThatBuffer(followUpTaskEvent.getPayload())
            .hasCapacity(modifiedPayload.length)
            .hasBytes(modifiedPayload);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldFailTask() throws InterruptedException, ExecutionException
    {
        // given
        final TaskSubscription subscription = new TaskSubscription()
                .setSubscriberKey(1L)
                .setChannelId(11)
                .setTaskType(TASK_TYPE_BUFFER)
                .setLockDuration(Duration.ofMinutes(5).toMillis())
                .setLockOwner(wrapString("owner"))
                .setCredits(10);

        lockTaskStreamProcessor.addSubscription(subscription);

        final TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setRetries(3)
            .setPayload(new UnsafeBuffer(MSGPACK_PAYLOAD));

        long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        LoggedEvent event = assertThatEventIsFollowedBy(position, TaskEventType.CREATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        // when
        taskEvent
            .setEventType(TaskEventType.FAIL)
            .setLockOwner(wrapString("owner"))
            .setRetries(2);

        position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        // then
        event = assertThatEventIsFollowedBy(position, TaskEventType.FAILED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldExpireTaskLock() throws InterruptedException, ExecutionException
    {
        // given
        final Instant now = Instant.now();
        final Duration lockDuration = Duration.ofMinutes(5);

        ClockUtil.setCurrentTime(now);

        final TaskSubscription subscription = new TaskSubscription()
                .setSubscriberKey(1L)
                .setChannelId(11)
                .setTaskType(TASK_TYPE_BUFFER)
                .setLockDuration(lockDuration.toMillis())
                .setLockOwner(wrapString("owner"))
                .setCredits(10);

        lockTaskStreamProcessor.addSubscription(subscription);

        final TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
            .setRetries(3)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setPayload(new UnsafeBuffer(MSGPACK_PAYLOAD));

        final long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        LoggedEvent event = assertThatEventIsFollowedBy(position, TaskEventType.CREATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        agentRunnerService.waitUntilDone();

        // when
        ClockUtil.setCurrentTime(now.plus(lockDuration));

        taskExpireLockStreamProcessor.checkLockExpirationAsync();

        // then
        event = assertThatEventIsFollowedBy(event, TaskEventType.EXPIRE_LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCK_EXPIRED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        assertThat(followUpTaskEvent.getLockTime()).isGreaterThan(now.plus(lockDuration).toEpochMilli());
        assertThat(followUpTaskEvent.getLockOwner()).isEqualTo(wrapString("owner"));

        assertThatBuffer(followUpTaskEvent.getPayload())
            .hasCapacity(MSGPACK_PAYLOAD.length)
            .hasBytes(MSGPACK_PAYLOAD);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
        verify(mockSubscribedEventWriter, times(2)).tryWriteMessage();
    }

    @Test
    public void shouldUpdateTaskRetries() throws InterruptedException, ExecutionException
    {
        // given
        final TaskSubscription subscription = new TaskSubscription()
                .setSubscriberKey(1L)
                .setChannelId(11)
                .setTaskType(TASK_TYPE_BUFFER)
                .setLockDuration(Duration.ofMinutes(5).toMillis())
                .setLockOwner(wrapString("owner"))
                .setCredits(10);

        lockTaskStreamProcessor.addSubscription(subscription);

        final TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setRetries(1)
            .setPayload(new UnsafeBuffer(MSGPACK_PAYLOAD));

        long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        LoggedEvent event = assertThatEventIsFollowedBy(position, TaskEventType.CREATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        taskEvent
            .setEventType(TaskEventType.FAIL)
            .setLockOwner(wrapString("owner"))
            .setRetries(0);

        position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        event = assertThatEventIsFollowedBy(position, TaskEventType.FAILED);
        logStream.setCommitPosition(event.getPosition());

        // when
        taskEvent
            .setEventType(TaskEventType.UPDATE_RETRIES)
            .setRetries(2);

        position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();
        logStream.setCommitPosition(position);

        // then
        event = assertThatEventIsFollowedBy(position, TaskEventType.RETRIES_UPDATED);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCK);
        logStream.setCommitPosition(event.getPosition());

        event = assertThatEventIsFollowedBy(event, TaskEventType.LOCKED);
        logStream.setCommitPosition(event.getPosition());

        verify(mockResponseWriter, times(3)).tryWriteResponse();

        assertThat(followUpTaskEvent.getRetries()).isEqualTo(2);
    }

    private LoggedEvent assertThatEventIsFollowedBy(LoggedEvent event, TaskEventType eventType)
    {
        return assertThatEventIsFollowedBy(event.getPosition(), eventType);
    }

    private LoggedEvent assertThatEventIsFollowedBy(long position, TaskEventType eventType)
    {
        final LoggedEvent event = getResultEventOf(position);

        followUpTaskEvent.reset();
        event.readValue(followUpTaskEvent);

        assertThat(followUpTaskEvent.getEventType()).isEqualTo(eventType);

        return event;
    }

    private LoggedEvent getResultEventOf(long position)
    {
        agentRunnerService.waitUntilDone();

        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream, true);

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
}
