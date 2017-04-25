package org.camunda.tngp.broker.transport.clientapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;
import static org.camunda.tngp.util.StringUtil.getBytes;
import static org.camunda.tngp.util.VarDataUtil.readBytes;
import static org.camunda.tngp.util.buffer.BufferUtil.wrapString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.event.processor.TopicSubscriptionService;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.taskqueue.TaskSubscriptionManager;
import org.camunda.tngp.broker.transport.controlmessage.ControlMessageRequestHeaderDescriptor;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.protocol.clientapi.ControlMessageRequestDecoder;
import org.camunda.tngp.protocol.clientapi.ControlMessageRequestEncoder;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandRequestEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.test.util.FluentMock;
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
import org.junit.rules.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

public class ClientApiMessageHandlerTest
{
    protected static final int TRANSPORT_CHANNEL_ID = 21;
    private static final int CONNECTION_ID = 4;
    private static final int REQUEST_ID = 5;

    protected static final DirectBuffer LOG_STREAM_TOPIC_NAME = wrapString("test-topic");
    protected static final int LOG_STREAM_PARTITION_ID = 1;
    protected static final byte[] COMMAND = getBytes("test-command");

    protected final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);
    protected final UnsafeBuffer sendBuffer = new UnsafeBuffer(new byte[1024 * 1024]);

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandRequestEncoder commandRequestEncoder = new ExecuteCommandRequestEncoder();
    protected final ControlMessageRequestEncoder controlRequestEncoder = new ControlMessageRequestEncoder();
    protected final ControlMessageRequestDecoder controlRequestDecoder = new ControlMessageRequestDecoder();
    protected final ControlMessageRequestHeaderDescriptor controlMessageRequestHeaderDescriptor = new ControlMessageRequestHeaderDescriptor();

    int fragmentOffset = 0;

    private LogStream logStream;
    private ClientApiMessageHandler messageHandler;

    @Mock
    private TransportChannel mockTransportChannel;

    @Mock
    private Dispatcher mockSendBuffer;

    @Mock
    private Dispatcher mockControlMessageDispatcher;

    @FluentMock
    private ErrorResponseWriter mockErrorResponseWriter;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public Timeout testTimeout = Timeout.seconds(5);

    @Before
    public void setup()
    {
        MockitoAnnotations.initMocks(this);

        when(mockTransportChannel.getId()).thenReturn(TRANSPORT_CHANNEL_ID);

        final AgentRunnerService agentRunnerService = new SharedAgentRunnerService(new SimpleAgentRunnerFactory(), "test");

        logStream = LogStreams.createFsLogStream(LOG_STREAM_TOPIC_NAME, LOG_STREAM_PARTITION_ID)
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .agentRunnerService(agentRunnerService)
            .writeBufferAgentRunnerService(agentRunnerService)
            .build();

        logStream.open();

        messageHandler = new ClientApiMessageHandler(
                mockSendBuffer,
                mockControlMessageDispatcher,
                mockErrorResponseWriter,
                mock(TopicSubscriptionService.class),
                mock(TaskSubscriptionManager.class));

        messageHandler.addStream(logStream);
    }

    @After
    public void cleanUp()
    {
        logStream.close();
    }

    @Test
    public void shouldHandleCommandRequest() throws InterruptedException, ExecutionException
    {
        // given
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_TOPIC_NAME, LOG_STREAM_PARTITION_ID);

        // when
        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream);
        waitForAvailableEvent(logStreamReader);

        final LoggedEvent loggedEvent = logStreamReader.next();

        final byte[] valueBuffer = new byte[COMMAND.length];
        loggedEvent.getValueBuffer().getBytes(loggedEvent.getValueOffset(), valueBuffer, 0, loggedEvent.getValueLength());

        assertThat(loggedEvent.getValueLength()).isEqualTo(COMMAND.length);
        assertThat(valueBuffer).isEqualTo(COMMAND);

        final BrokerEventMetadata eventMetadata = new BrokerEventMetadata();
        loggedEvent.readMetadata(eventMetadata);

        assertThat(eventMetadata.getReqChannelId()).isEqualTo(TRANSPORT_CHANNEL_ID);
        assertThat(eventMetadata.getReqConnectionId()).isEqualTo(CONNECTION_ID);
        assertThat(eventMetadata.getReqRequestId()).isEqualTo(REQUEST_ID);
    }

    @Test
    public void shouldWriteCommandRequestProtocolVersion() throws InterruptedException, ExecutionException
    {
        // given
        final short clientProtocolVersion = Constants.PROTOCOL_VERSION - 1;
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_TOPIC_NAME, LOG_STREAM_PARTITION_ID, clientProtocolVersion);

        // when
        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream);
        waitForAvailableEvent(logStreamReader);

        final LoggedEvent loggedEvent = logStreamReader.next();
        final BrokerEventMetadata eventMetadata = new BrokerEventMetadata();
        loggedEvent.readMetadata(eventMetadata);

        assertThat(eventMetadata.getProtocolVersion()).isEqualTo(clientProtocolVersion);
    }

    @Test
    public void shouldWriteCommandRequestEventType() throws InterruptedException, ExecutionException
    {
        // given
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_TOPIC_NAME, LOG_STREAM_PARTITION_ID, null, TASK_EVENT);

        // when
        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream);
        waitForAvailableEvent(logStreamReader);

        final LoggedEvent loggedEvent = logStreamReader.next();
        final BrokerEventMetadata eventMetadata = new BrokerEventMetadata();
        loggedEvent.readMetadata(eventMetadata);

        assertThat(eventMetadata.getEventType()).isEqualTo(TASK_EVENT);
    }

    @Test
    public void shouldHandleControlRequest() throws InterruptedException, ExecutionException
    {
        // given
        final int writtenLength = writeControlRequestToBuffer(buffer);

        when(mockControlMessageDispatcher.claim(any(ClaimedFragment.class), anyInt())).thenAnswer(claimFragment(0));

        // when
        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        verify(mockControlMessageDispatcher).claim(any(ClaimedFragment.class), anyInt());

        int offset = fragmentOffset;

        controlMessageRequestHeaderDescriptor.wrap(sendBuffer, offset);

        assertThat(controlMessageRequestHeaderDescriptor.channelId()).isEqualTo(TRANSPORT_CHANNEL_ID);
        assertThat(controlMessageRequestHeaderDescriptor.connectionId()).isEqualTo(CONNECTION_ID);
        assertThat(controlMessageRequestHeaderDescriptor.requestId()).isEqualTo(REQUEST_ID);

        offset += ControlMessageRequestHeaderDescriptor.headerLength();

        controlRequestDecoder.wrap(sendBuffer, offset, controlRequestDecoder.sbeBlockLength(), controlRequestDecoder.sbeSchemaVersion());

        final byte[] requestData = readBytes(controlRequestDecoder::getData, controlRequestDecoder::dataLength);

        assertThat(requestData).isEqualTo(COMMAND);
    }

    @Test
    public void shouldSendErrorMessageIfTopicNotFound()
    {
        // given
        final int writtenLength = writeCommandRequestToBuffer(buffer, wrapString("unknown-topic"), LOG_STREAM_PARTITION_ID);

        when(mockSendBuffer.claim(any(ClaimedFragment.class), anyInt())).thenAnswer(claimFragment(0));

        // when
        when(mockErrorResponseWriter.tryWriteResponseOrLogFailure()).thenReturn(true);

        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        verify(mockErrorResponseWriter).errorCode(ErrorCode.TOPIC_NOT_FOUND);
        verify(mockErrorResponseWriter).errorMessage("Cannot execute command. Topic with name '%s' and partition id '%d' not found", "unknown-topic", LOG_STREAM_PARTITION_ID);
        verify(mockErrorResponseWriter).tryWriteResponseOrLogFailure();
    }

    @Test
    public void shouldNotHandleUnkownRequest() throws InterruptedException, ExecutionException
    {
        // given
        int offset = 0;

        final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
        transportHeaderDescriptor.wrap(buffer, offset).protocolId(Protocols.REQUEST_RESPONSE);

        offset += TransportHeaderDescriptor.headerLength();
        offset += RequestResponseProtocolHeaderDescriptor.headerLength();

        headerEncoder.wrap(buffer, offset)
            .blockLength(commandRequestEncoder.sbeBlockLength())
            .schemaId(commandRequestEncoder.sbeSchemaId())
            .templateId(999)
            .version(1);

        final int writtenLength =
                TransportHeaderDescriptor.headerLength() +
                RequestResponseProtocolHeaderDescriptor.headerLength() +
                headerEncoder.encodedLength();

        // when
        when(mockErrorResponseWriter.tryWriteResponseOrLogFailure()).thenReturn(true);

        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        verify(mockErrorResponseWriter).errorCode(ErrorCode.MESSAGE_NOT_SUPPORTED);
        verify(mockErrorResponseWriter).errorMessage("Cannot handle message. Template partitionId '%d' is not supported.", 999);
        verify(mockErrorResponseWriter).tryWriteResponseOrLogFailure();
    }

    @Test
    public void shouldSendErrorMessageOnRequestWithNewerProtocolVersion()
    {
        // given
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_TOPIC_NAME, LOG_STREAM_PARTITION_ID, Short.MAX_VALUE);

        when(mockSendBuffer.claim(any(ClaimedFragment.class), anyInt())).thenAnswer(claimFragment(0));
        when(mockErrorResponseWriter.tryWriteResponseOrLogFailure()).thenReturn(true);

        // when
        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        verify(mockErrorResponseWriter).errorCode(ErrorCode.INVALID_CLIENT_VERSION);
        verify(mockErrorResponseWriter).errorMessage("Client has newer version than broker (%d > %d)", (int) Short.MAX_VALUE, ExecuteCommandRequestEncoder.SCHEMA_VERSION);
        verify(mockErrorResponseWriter).tryWriteResponseOrLogFailure();
    }

    private int writeCommandRequestToBuffer(UnsafeBuffer buffer, DirectBuffer topicName, int partitionId)
    {
        return writeCommandRequestToBuffer(buffer, topicName, partitionId, null);
    }

    protected int writeCommandRequestToBuffer(UnsafeBuffer buffer, DirectBuffer topicName, int partitionId, Short protocolVersion)
    {
        return writeCommandRequestToBuffer(buffer, topicName, partitionId, protocolVersion, null);
    }

    protected int writeCommandRequestToBuffer(UnsafeBuffer buffer, DirectBuffer topicName, int partitionId, Short protocolVersion, EventType eventType)
    {
        int offset = 0;

        final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
        transportHeaderDescriptor.wrap(buffer, offset).protocolId(Protocols.REQUEST_RESPONSE);

        offset += TransportHeaderDescriptor.headerLength();

        final RequestResponseProtocolHeaderDescriptor requestResponseProtocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();
        requestResponseProtocolHeaderDescriptor.wrap(buffer, offset)
            .connectionId(CONNECTION_ID)
            .requestId(REQUEST_ID);

        offset += RequestResponseProtocolHeaderDescriptor.headerLength();

        final int protocolVersionToWrite = protocolVersion != null ? protocolVersion : commandRequestEncoder.sbeSchemaVersion();
        final EventType eventTypeToWrite = eventType != null ? eventType : EventType.NULL_VAL;

        headerEncoder.wrap(buffer, offset)
            .blockLength(commandRequestEncoder.sbeBlockLength())
            .schemaId(commandRequestEncoder.sbeSchemaId())
            .templateId(commandRequestEncoder.sbeTemplateId())
            .version(protocolVersionToWrite);

        offset += headerEncoder.encodedLength();

        commandRequestEncoder.wrap(buffer, offset);

        commandRequestEncoder
            .partitionId(partitionId)
            .eventType(eventTypeToWrite)
            .putTopicName(topicName, 0, topicName.capacity())
            .putCommand(COMMAND, 0, COMMAND.length);

        return TransportHeaderDescriptor.headerLength() +
                RequestResponseProtocolHeaderDescriptor.headerLength() +
                headerEncoder.encodedLength() +
                commandRequestEncoder.encodedLength();
    }

    private int writeControlRequestToBuffer(UnsafeBuffer buffer)
    {
        int offset = 0;

        final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
        transportHeaderDescriptor.wrap(buffer, offset).protocolId(Protocols.REQUEST_RESPONSE);

        offset += TransportHeaderDescriptor.headerLength();

        final RequestResponseProtocolHeaderDescriptor requestResponseProtocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();
        requestResponseProtocolHeaderDescriptor.wrap(buffer, offset)
            .connectionId(CONNECTION_ID)
            .requestId(REQUEST_ID);

        offset += RequestResponseProtocolHeaderDescriptor.headerLength();

        headerEncoder.wrap(buffer, offset)
            .blockLength(controlRequestEncoder.sbeBlockLength())
            .schemaId(controlRequestEncoder.sbeSchemaId())
            .templateId(controlRequestEncoder.sbeTemplateId())
            .version(controlRequestEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        controlRequestEncoder.wrap(buffer, offset);

        controlRequestEncoder.putData(COMMAND, 0, COMMAND.length);

        return TransportHeaderDescriptor.headerLength() +
                RequestResponseProtocolHeaderDescriptor.headerLength() +
                headerEncoder.encodedLength() +
                controlRequestEncoder.encodedLength();
    }

    protected Answer<?> claimFragment(final long offset)
    {
        return invocation ->
        {
            final ClaimedFragment claimedFragment = (ClaimedFragment) invocation.getArguments()[0];
            final int length = (int) invocation.getArguments()[1];

            fragmentOffset = claimedFragment.getOffset();

            claimedFragment.wrap(sendBuffer, 0, alignedLength(length));

            final long claimedPosition = offset + alignedLength(length);
            return claimedPosition;
        };
    }

    protected void waitForAvailableEvent(BufferedLogStreamReader logStreamReader)
    {
        while (!logStreamReader.hasNext())
        {
            // wait for event
        }
    }

}
