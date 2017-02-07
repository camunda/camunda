package org.camunda.tngp.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.taskqueue.processor.TaskInstanceStreamProcessor;
import org.camunda.tngp.broker.transport.clientapi.ClientApiMessageHandler;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.transport.clientapi.ErrorResponseWriter;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandRequestEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.agent.SharedAgentRunnerService;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class IntegrationTest
{
    protected final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);

    private LogStream logStream;
    private ClientApiMessageHandler messageHandler;
    private StreamProcessorController streamProcessorController;

    @Mock
    private TransportChannel mockTransportChannel;

    @Mock
    private Dispatcher mockSendBuffer;

    @Mock
    private Dispatcher mockControlMessageDispatcher;

    @Mock
    private ErrorResponseWriter mockErrorResponseWriter;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();


    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        MockitoAnnotations.initMocks(this);

        final AgentRunnerService agentRunnerService = new SharedAgentRunnerService(new SimpleAgentRunnerFactory(), "test");

        logStream = LogStreams.createFsLogStream("test-log", 0)
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .agentRunnerService(agentRunnerService)
            .writeBufferAgentRunnerService(agentRunnerService)
            .build();

        logStream.open();

        messageHandler = new ClientApiMessageHandler(mockSendBuffer, mockControlMessageDispatcher, mockErrorResponseWriter);

        messageHandler.addStream(logStream);

        final SnapshotStorage snapshotStorage = LogStreams.createFsSnapshotStore(tempFolder.getRoot().getAbsolutePath()).build();

        final FileChannelIndexStore indexStore = FileChannelIndexStore.tempFileIndexStore();

        final CommandResponseWriter responseWriter = mock(CommandResponseWriter.class, new Answer<CommandResponseWriter>()
        {

            @Override
            public CommandResponseWriter answer(InvocationOnMock invocation) throws Throwable
            {
                return (CommandResponseWriter) invocation.getMock();
            }
        });

        doReturn(true).when(responseWriter).tryWriteResponse();

        streamProcessorController = LogStreams.createStreamProcessor("task-test", 0, new TaskInstanceStreamProcessor(responseWriter, indexStore))
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
    public void shoulProcessTaskEvent() throws InterruptedException, ExecutionException
    {
        // given
        final DirectBuffer typeBuffer = new UnsafeBuffer("test-task".getBytes(StandardCharsets.UTF_8));

        final TaskEvent taskEvent = new TaskEvent()
            .setEventType(TaskEventType.CREATE)
            .setType(typeBuffer, 0, typeBuffer.capacity());

        final int writtenLength = writeCommandRequestToBuffer(buffer, taskEvent);

        taskEvent.reset();

        // when
        final boolean result = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(result).isTrue();

        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream);

        LoggedEvent loggedEvent = null;
        int eventCount = 0;

        for (int i = 0; i < 100_000 && eventCount < 2; i++)
        {
            if (logStreamReader.hasNext())
            {
                loggedEvent = logStreamReader.next();
                eventCount += 1;
            }
        }

        loggedEvent.readValue(taskEvent);

        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.CREATED);
        assertThatBuffer(taskEvent.getType()).hasBytes("test-task".getBytes(StandardCharsets.UTF_8));
    }

    private int writeCommandRequestToBuffer(UnsafeBuffer buffer, TaskEvent taskEvent)
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final ExecuteCommandRequestEncoder commandRequestEncoder = new ExecuteCommandRequestEncoder();

        int offset = 0;

        final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
        transportHeaderDescriptor.wrap(buffer, offset).protocolId(Protocols.REQUEST_RESPONSE);

        offset += TransportHeaderDescriptor.headerLength();

        offset += RequestResponseProtocolHeaderDescriptor.headerLength();

        headerEncoder.wrap(buffer, offset)
            .blockLength(commandRequestEncoder.sbeBlockLength())
            .schemaId(commandRequestEncoder.sbeSchemaId())
            .templateId(commandRequestEncoder.sbeTemplateId())
            .version(commandRequestEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        commandRequestEncoder.wrap(buffer, offset);

        commandRequestEncoder.topicId(0);

        final UnsafeBuffer commandBuffer = new UnsafeBuffer(new byte[taskEvent.getLength()]);
        taskEvent.write(commandBuffer, 0);
        commandRequestEncoder.putCommand(commandBuffer, 0, taskEvent.getLength());

        return TransportHeaderDescriptor.headerLength() +
                RequestResponseProtocolHeaderDescriptor.headerLength() +
                headerEncoder.encodedLength() +
                commandRequestEncoder.encodedLength();
    }

}
