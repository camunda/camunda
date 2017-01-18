package org.camunda.tngp.broker.taskqueue.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.util.msgpack.value.StringValue;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
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

public class TaskInstanceStreamProcessorTest
{
    private static final int LOG_ID = 1;

    private static final byte[] PAYLOAD = "payload".getBytes();

    private LogStream logStream;
    private StreamProcessorController streamProcessorController;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private CommandResponseWriter mockResponseWriter;

    private LogStreamWriter logStreamWriter;

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        final AgentRunnerService agentRunnerService = new SharedAgentRunnerService(new SimpleAgentRunnerFactory(), "test");

        logStream = LogStreams.createFsLogStream("test-log", LOG_ID)
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .agentRunnerService(agentRunnerService)
            .build();

        logStream.open();

        mockResponseWriter = mock(CommandResponseWriter.class);

        when(mockResponseWriter.brokerEventMetadata(any())).thenReturn(mockResponseWriter);
        when(mockResponseWriter.longKey(anyLong())).thenReturn(mockResponseWriter);
        when(mockResponseWriter.topicId(anyInt())).thenReturn(mockResponseWriter);
        when(mockResponseWriter.eventWriter(any())).thenReturn(mockResponseWriter);
        when(mockResponseWriter.tryWriteResponse()).thenReturn(true);

        final SnapshotStorage snapshotStorage = LogStreams.createFsSnapshotStore(tempFolder.getRoot().getAbsolutePath()).build();

        final FileChannelIndexStore indexStore = FileChannelIndexStore.tempFileIndexStore();

        streamProcessorController = LogStreams.createStreamProcessor("task-test", 0, new TaskInstanceStreamProcessor(mockResponseWriter, indexStore))
            .sourceStream(logStream)
            .targetStream(logStream)
            .snapshotStorage(snapshotStorage)
            .agentRunnerService(agentRunnerService)
            .build();

        streamProcessorController.openAsync().get();

        logStreamWriter = new LogStreamWriter(logStream);
    }

    @After
    public void cleanUp() throws InterruptedException, ExecutionException
    {
        streamProcessorController.closeAsync().get();

        logStream.close();
    }

    @Test
    public void shouldProcessCreateEvent() throws InterruptedException, ExecutionException
    {
        // given
        TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
            .setType(new StringValue("test-task"))
            .setPayload(new UnsafeBuffer(PAYLOAD));

        logStreamWriter
            .key(2L)
            .valueWriter(taskEvent)
            .tryWrite();

        // then
        taskEvent = getTaskEvent(2);

        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.CREATED);
        assertThat(taskEvent.getType().toString()).isEqualTo("test-task");

        assertThatBuffer(taskEvent.getPayload())
            .hasCapacity(PAYLOAD.length)
            .hasBytes(PAYLOAD);

        verify(mockResponseWriter).topicId(LOG_ID);
        verify(mockResponseWriter).longKey(2L);
        verify(mockResponseWriter).tryWriteResponse();
    }

    private TaskEvent getTaskEvent(int eventNumber)
    {
        final TaskEvent taskEvent = new TaskEvent();
        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream);

        LoggedEvent loggedEvent = null;
        int eventCount = 0;

        do
        {
            if (logStreamReader.hasNext())
            {
                loggedEvent = logStreamReader.next();
                eventCount += 1;
            }
        } while (eventCount < eventNumber);

        loggedEvent.readValue(taskEvent);

        return taskEvent;
    }
}
