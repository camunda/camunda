package org.camunda.tngp.broker.taskqueue.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.test.util.FluentAnswer;
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

public class TaskStreamProcessorInterationTest
{
    private static final int LOG_ID = 1;

    private static final byte[] TASK_TYPE = "test-task".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PAYLOAD = "payload".getBytes();

    private static final DirectBuffer TASK_TYPE_BUFFER = new UnsafeBuffer(TASK_TYPE);

    private LogStream logStream;
    private StreamProcessorController streamProcessorController;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private CommandResponseWriter mockResponseWriter;
    private LogStreamWriter logStreamWriter;
    private AgentRunnerService agentRunnerService;

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        agentRunnerService = new SharedAgentRunnerService(new SimpleAgentRunnerFactory(), "test");

        logStream = LogStreams.createFsLogStream("test-log", LOG_ID)
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .agentRunnerService(agentRunnerService)
            .writeBufferAgentRunnerService(agentRunnerService)
            .build();

        logStream.open();

        mockResponseWriter = mock(CommandResponseWriter.class, new FluentAnswer());
        when(mockResponseWriter.tryWriteResponse()).thenReturn(true);

        final SnapshotStorage snapshotStorage = LogStreams.createFsSnapshotStore(tempFolder.getRoot().getAbsolutePath()).build();
        final FileChannelIndexStore indexStore = FileChannelIndexStore.tempFileIndexStore();

        final StreamProcessor streamProcessor = new TaskInstanceStreamProcessor(mockResponseWriter, indexStore);
        streamProcessorController = LogStreams.createStreamProcessor("task-test", 0, streamProcessor)
            .sourceStream(logStream)
            .targetStream(logStream)
            .snapshotStorage(snapshotStorage)
            .agentRunnerService(agentRunnerService)
            .build();

        streamProcessorController.openAsync().get();

        logStreamWriter = new LogStreamWriter(logStream);
    }

    @After
    public void cleanUp() throws Exception
    {
        streamProcessorController.closeAsync().get();

        logStream.close();

        agentRunnerService.close();
    }

    @Test
    public void shouldCreateTask() throws InterruptedException, ExecutionException
    {
        // given
        TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity())
            .setPayload(new UnsafeBuffer(PAYLOAD));

        final long position = logStreamWriter
            .key(2L)
            .valueWriter(taskEvent)
            .tryWrite();

        // then
        taskEvent = getResultTaskEventOf(position);

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
        TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
            .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity());

        logStreamWriter
            .key(2L)
            .valueWriter(taskEvent)
            .tryWrite();

        taskEvent
            .setEventType(TaskEventType.LOCK)
            .setLockTime(123);

        final long position = logStreamWriter
                .key(2L)
                .valueWriter(taskEvent)
                .tryWrite();

        // then
        taskEvent = getResultTaskEventOf(position);

        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.LOCKED);
        assertThat(taskEvent.getLockTime()).isEqualTo(123L);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    private TaskEvent getResultTaskEventOf(long position)
    {
        final TaskEvent taskEvent = new TaskEvent();
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

        loggedEvent.readValue(taskEvent);

        return taskEvent;
    }
}
