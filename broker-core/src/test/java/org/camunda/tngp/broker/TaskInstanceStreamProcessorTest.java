package org.camunda.tngp.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.concurrent.ExecutionException;

import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.taskqueue.processor.CmdResponseWriter;
import org.camunda.tngp.broker.taskqueue.processor.TaskInstanceStreamProcessor;
import org.camunda.tngp.broker.util.msgpack.value.StringValue;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.logstreams.snapshot.SerializableWrapper;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.agent.SharedAgentRunnerService;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TaskInstanceStreamProcessorTest
{
    private LogStream logStream;
    private StreamProcessorController streamProcessorController;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        final AgentRunnerService agentRunnerService = new SharedAgentRunnerService(new SimpleAgentRunnerFactory(), "test");

        logStream = LogStreams.createFsLogStream("test-log", 0)
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .agentRunnerService(agentRunnerService)
            .build();

        logStream.open();

        final SnapshotStorage snapshotStorage = LogStreams.createFsSnapshotStore(tempFolder.getRoot().getAbsolutePath()).build();

        final CmdResponseWriter responseWriter = mock(CmdResponseWriter.class, new Answer<CmdResponseWriter>()
        {

            @Override
            public CmdResponseWriter answer(InvocationOnMock invocation) throws Throwable
            {
                return (CmdResponseWriter) invocation.getMock();
            }
        });

        doReturn(true).when(responseWriter).tryWriteResponse();

        // TODO use a stream processor resource
        streamProcessorController = LogStreams.createStreamProcessor("task-test", 0, new TaskInstanceStreamProcessor(responseWriter))
            .resource(new SerializableWrapper<>("foo"))
            .sourceStream(logStream)
            .targetStream(logStream)
            .snapshotStorage(snapshotStorage)
            .agentRunnerService(agentRunnerService)
            .build();

        streamProcessorController.openAsync().get();
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
        final TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
            .setType(new StringValue("test-task"));

        // when
        final LogStreamWriter logStreamWriter = new LogStreamWriter(logStream);

        logStreamWriter
            .positionAsKey()
            .valueWriter(taskEvent)
            .tryWrite();

        taskEvent.reset();

        // then
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
        } while (eventCount < 2);

        loggedEvent.readValue(taskEvent);

        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.CREATED);
        assertThat(taskEvent.getType().toString()).isEqualTo("test-task");
    }
}
