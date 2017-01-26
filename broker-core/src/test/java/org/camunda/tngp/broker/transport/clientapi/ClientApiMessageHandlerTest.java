package org.camunda.tngp.broker.transport.clientapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.test.util.FluentAnswer;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.protocol.clientapi.ControlMessageRequestEncoder;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
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
import org.mockito.stubbing.Answer;

public class ClientApiMessageHandlerTest
{
    protected static final int TRANSPORT_CHANNEL_ID = 21;
    private static final int CONNECTION_ID = 4;
    private static final int REQUEST_ID = 5;

    protected static final int LOG_STREAM_ID = 1;
    protected static final byte[] COMMAND = "test-command".getBytes();

    protected final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);
    protected final UnsafeBuffer sendBuffer = new UnsafeBuffer(new byte[1024 * 1024]);

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandRequestEncoder commandRequestEncoder = new ExecuteCommandRequestEncoder();
    protected final ControlMessageRequestEncoder controlRequestEncoder = new ControlMessageRequestEncoder();

    int fragmentOffset = 0;

    private LogStream logStream;
    private ClientApiMessageHandler messageHandler;

    @Mock
    private TransportChannel mockTransportChannel;

    @Mock
    private Dispatcher mockSendBuffer;

    @Mock
    private Dispatcher mockControlMessageDispatcher;

    //@Mock
    private ErrorResponseWriter mockErrorResponseWriter;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setup()
    {
        MockitoAnnotations.initMocks(this);

        mockErrorResponseWriter = mock(ErrorResponseWriter.class, new FluentAnswer());

        when(mockTransportChannel.getId()).thenReturn(TRANSPORT_CHANNEL_ID);

        final AgentRunnerService agentRunnerService = new SharedAgentRunnerService(new SimpleAgentRunnerFactory(), "test");

        logStream = LogStreams.createFsLogStream("test-log", LOG_STREAM_ID)
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .agentRunnerService(agentRunnerService)
            .writeBufferAgentRunnerService(agentRunnerService)
            .build();

        logStream.open();

        messageHandler = new ClientApiMessageHandler(mockSendBuffer, mockControlMessageDispatcher, mockErrorResponseWriter);

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
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_ID);

        // when
        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream);

        while (!logStreamReader.hasNext())
        {
            // wait for event
        }

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
    public void shouldHandleControlRequest() throws InterruptedException, ExecutionException
    {
        // given
        final int writtenLength = writeControlRequestToBuffer(buffer);

        when(mockControlMessageDispatcher.offer(any(), anyInt(), anyInt(), anyInt())).thenReturn(12L);

        // when
        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        verify(mockControlMessageDispatcher).offer(buffer, controlRequestEncoder.offset(), controlRequestEncoder.encodedLength(), TRANSPORT_CHANNEL_ID);
    }

    @Test
    public void shouldSendErrorMessageIfTopicNotFound()
    {
        // given
        final int writtenLength = writeCommandRequestToBuffer(buffer, 9);

        when(mockSendBuffer.claim(any(ClaimedFragment.class), anyInt())).thenAnswer(claimFragment(0));

        // when
        when(mockErrorResponseWriter.tryWriteResponse()).thenReturn(true);

        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        verify(mockErrorResponseWriter).errorCode(ErrorCode.TOPIC_NOT_FOUND);
        verify(mockErrorResponseWriter).errorMessage("Cannot execute command. Topic with id '%d' not found", 9L);
        verify(mockErrorResponseWriter).tryWriteResponse();
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
        when(mockErrorResponseWriter.tryWriteResponse()).thenReturn(true);

        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        verify(mockErrorResponseWriter).errorCode(ErrorCode.MESSAGE_NOT_SUPPORTED);
        verify(mockErrorResponseWriter).errorMessage("Cannot handle message. Template id '%d' is not supported.", 999);
        verify(mockErrorResponseWriter).tryWriteResponse();
    }

    @Test
    public void shouldSendErrorMessageOnRequestWithNewerProtocolVersion()
    {
        // given
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_ID, Short.MAX_VALUE);

        when(mockSendBuffer.claim(any(ClaimedFragment.class), anyInt())).thenAnswer(claimFragment(0));
        when(mockErrorResponseWriter.tryWriteResponse()).thenReturn(true);

        // when
        final boolean isHandled = messageHandler.handleMessage(mockTransportChannel, buffer, 0, writtenLength);

        // then
        assertThat(isHandled).isTrue();

        verify(mockErrorResponseWriter).errorCode(ErrorCode.INVALID_CLIENT_VERSION);
        verify(mockErrorResponseWriter).errorMessage("Client has newer version than broker (%d > %d)", (int) Short.MAX_VALUE, ExecuteCommandRequestEncoder.SCHEMA_VERSION);
        verify(mockErrorResponseWriter).tryWriteResponse();
    }

    private int writeCommandRequestToBuffer(UnsafeBuffer buffer, int topicId)
    {
        return writeCommandRequestToBuffer(buffer, topicId, null);
    }

    protected int writeCommandRequestToBuffer(UnsafeBuffer buffer, int topicId, Short protocolVersion)
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

        headerEncoder.wrap(buffer, offset)
            .blockLength(commandRequestEncoder.sbeBlockLength())
            .schemaId(commandRequestEncoder.sbeSchemaId())
            .templateId(commandRequestEncoder.sbeTemplateId())
            .version(protocolVersionToWrite);

        offset += headerEncoder.encodedLength();

        commandRequestEncoder.wrap(buffer, offset);

        commandRequestEncoder
            .topicId(topicId)
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

}
