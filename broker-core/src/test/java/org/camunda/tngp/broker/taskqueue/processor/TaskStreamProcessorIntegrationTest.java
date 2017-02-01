package org.camunda.tngp.broker.taskqueue.processor;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.broker.test.util.BufferAssert.*;
import static org.camunda.tngp.protocol.clientapi.EventType.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.agent.SharedAgentRunnerService;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockitoAnnotations;

public class TaskStreamProcessorIntegrationTest
{
    private static final int LOG_ID = 1;

    private static final byte[] TASK_TYPE = "test-task".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PAYLOAD = "payload".getBytes();

    private static final DirectBuffer TASK_TYPE_BUFFER = new UnsafeBuffer(TASK_TYPE);

    private LogStream logStream;

    private LockTaskStreamProcessor lockTaskStreamProcessor;

    private StreamProcessorController taskInstanceStreamProcessorController;
    private StreamProcessorController taskSubscriptionStreamProcessorController;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @FluentMock
    private CommandResponseWriter mockResponseWriter;

    private LogStreamWriter logStreamWriter;
    private AgentRunnerService agentRunnerService;

    private final BrokerEventMetadata defaultBrokerEventMetadata = new BrokerEventMetadata();
    private BrokerEventMetadata lastBrokerEventMetadata = new BrokerEventMetadata();

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        MockitoAnnotations.initMocks(this);

        when(mockResponseWriter.tryWriteResponse()).thenReturn(true);

        doAnswer(invocation ->
        {
            final BrokerEventMetadata metadata =  (BrokerEventMetadata) invocation.getArguments()[0];

            final UnsafeBuffer metadataBuffer = new UnsafeBuffer(new byte[metadata.getLength()]);
            metadata.write(metadataBuffer, 0);

            lastBrokerEventMetadata.wrap(metadataBuffer, 0, metadataBuffer.capacity());

            return invocation.getMock();
        }).when(mockResponseWriter).brokerEventMetadata(any(BrokerEventMetadata.class));

        agentRunnerService = new SharedAgentRunnerService(new SimpleAgentRunnerFactory(), "test");

        final String rootPath = tempFolder.getRoot().getAbsolutePath();

        logStream = LogStreams.createFsLogStream("test-log", LOG_ID)
            .logRootPath(rootPath)
            .agentRunnerService(agentRunnerService)
            .writeBufferAgentRunnerService(agentRunnerService)
            .build();

        logStream.open();

        final SnapshotStorage snapshotStorage = LogStreams.createFsSnapshotStore(rootPath).build();
        final FileChannelIndexStore indexStore = FileChannelIndexStore.tempFileIndexStore();

        final StreamProcessor taskInstanceStreamProcessor = new TaskInstanceStreamProcessor(mockResponseWriter, indexStore);
        taskInstanceStreamProcessorController = LogStreams.createStreamProcessor("task-instance", 0, taskInstanceStreamProcessor)
            .sourceStream(logStream)
            .targetStream(logStream)
            .snapshotStorage(snapshotStorage)
            .agentRunnerService(agentRunnerService)
            .build();

        lockTaskStreamProcessor = new LockTaskStreamProcessor();
        taskSubscriptionStreamProcessorController = LogStreams.createStreamProcessor("task-lock", 1, lockTaskStreamProcessor)
            .sourceStream(logStream)
            .targetStream(logStream)
            .snapshotStorage(snapshotStorage)
            .agentRunnerService(agentRunnerService)
            .build();

        taskInstanceStreamProcessorController.openAsync().get();
        taskSubscriptionStreamProcessorController.openAsync().get();

        logStreamWriter = new LogStreamWriter(logStream);
        defaultBrokerEventMetadata.eventType(TASK_EVENT);
    }

    @After
    public void cleanUp() throws Exception
    {
        taskInstanceStreamProcessorController.closeAsync().get();
        taskSubscriptionStreamProcessorController.closeAsync().get();

        logStream.close();

        agentRunnerService.close();
    }

    @Test
    public void shouldCreateTask() throws InterruptedException, ExecutionException
    {
        // given
        final TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setPayload(new UnsafeBuffer(PAYLOAD));

        // when
        final long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();

        // then
        getResultEventOf(position).readValue(taskEvent);

        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.CREATED);
        assertThatBuffer(taskEvent.getType()).hasBytes(TASK_TYPE_BUFFER.byteArray());

        assertThatBuffer(taskEvent.getPayload())
            .hasCapacity(PAYLOAD.length)
            .hasBytes(PAYLOAD);

        verify(mockResponseWriter).topicId(LOG_ID);
        verify(mockResponseWriter).longKey(2L);
        verify(mockResponseWriter).tryWriteResponse();
    }

    @Test
    public void shouldLockTask() throws InterruptedException, ExecutionException
    {
        // given
        final TaskSubscription subscription = new TaskSubscription()
                .setId(1L)
                .setChannelId(11)
                .setTaskType(TASK_TYPE_BUFFER)
                .setLockDuration(Duration.ofMinutes(5).toMillis())
                .setCredits(10);

        lockTaskStreamProcessor.addSubscription(subscription);

        final TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity());

        // when
        final long position = logStreamWriter
            .key(2L)
            .metadataWriter(defaultBrokerEventMetadata)
            .valueWriter(taskEvent)
            .tryWrite();

        // then
        LoggedEvent event = getResultEventOf(position);
        event.readValue(taskEvent);
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.CREATED);

        event = getResultEventOf(event.getPosition());
        event.readValue(taskEvent);
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.LOCK);
        assertThat(taskEvent.getLockTime()).isGreaterThan(0);

        event = getResultEventOf(event.getPosition());
        event.readValue(taskEvent);
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.LOCKED);
        assertThat(taskEvent.getLockTime()).isGreaterThan(0);

        verify(mockResponseWriter, times(2)).tryWriteResponse();

        assertThat(lastBrokerEventMetadata.getSubscriptionId()).isEqualTo(subscription.getId());
        assertThat(lastBrokerEventMetadata.getReqChannelId()).isEqualTo(subscription.getChannelId());
        assertThat(lastBrokerEventMetadata.getEventType()).isEqualTo(TASK_EVENT);
    }

    private LoggedEvent getResultEventOf(long position)
    {
        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream);

        LoggedEvent loggedEvent = null;
        long sourceEventPosition = -1;

        do
        {
            if (logStreamReader.hasNext())
            {
                loggedEvent = logStreamReader.next();
                sourceEventPosition = loggedEvent.getSourceEventPosition();
            }
        } while (sourceEventPosition < position);

        assertThat(sourceEventPosition).isEqualTo(position);

        return loggedEvent;
    }
}
