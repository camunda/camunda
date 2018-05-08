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
package io.zeebe.broker.transport.clientapi;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedFramedLength;
import static io.zeebe.util.VarDataUtil.readBytes;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.transport.controlmessage.ControlMessageRequestHeaderDescriptor;
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageRequestDecoder;
import io.zeebe.protocol.clientapi.ControlMessageRequestEncoder;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.util.sched.testing.ActorSchedulerRule;

public class ClientApiMessageHandlerTest
{
    private static final int REQUEST_ID = 5;
    private static final int RAFT_TERM = 10;
    protected static final RemoteAddress DEFAULT_ADDRESS = new RemoteAddressImpl(21, new SocketAddress("foo", 4242));

    protected static final DirectBuffer LOG_STREAM_TOPIC_NAME = wrapString("test-topic");
    protected static final int LOG_STREAM_PARTITION_ID = 1;

    protected static final byte[] JOB_EVENT;
    static
    {
        final JobRecord jobEvent = new JobRecord()
                .setType(wrapString("test"));

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[jobEvent.getEncodedLength()]);
        jobEvent.write(buffer, 0);

        JOB_EVENT = buffer.byteArray();
    }

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
    private Dispatcher mockControlMessageDispatcher;

    public TemporaryFolder tempFolder = new TemporaryFolder();

    public ActorSchedulerRule agentRunnerService = new ActorSchedulerRule();

    public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(agentRunnerService);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(tempFolder)
        .around(agentRunnerService)
        .around(serviceContainerRule);



    protected BufferingServerOutput serverOutput;

    @Before
    public void setup()
    {
        MockitoAnnotations.initMocks(this);

        serverOutput = new BufferingServerOutput();

        logStream = LogStreams.createFsLogStream(LOG_STREAM_TOPIC_NAME, LOG_STREAM_PARTITION_ID)
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .serviceContainer(serviceContainerRule.get())
            .logName("Test")
            .build().join();

        logStream.openAppender().join();

        messageHandler = new ClientApiMessageHandler(mockControlMessageDispatcher);

        final Partition partition = new Partition(new PartitionInfo(LOG_STREAM_TOPIC_NAME, LOG_STREAM_PARTITION_ID, 1), RaftState.LEADER)
        {
            @Override
            public LogStream getLogStream()
            {
                return logStream;
            }
        };

        messageHandler.addPartition(partition);
        logStream.setTerm(RAFT_TERM);
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
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_PARTITION_ID, null, ValueType.JOB, JobIntent.CREATE);

        // when
        final boolean isHandled = messageHandler.onRequest(serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);

        // then
        assertThat(isHandled).isTrue();

        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream, true);
        waitForAvailableEvent(logStreamReader);

        final LoggedEvent loggedEvent = logStreamReader.next();

        final byte[] valueBuffer = new byte[JOB_EVENT.length];
        loggedEvent.getValueBuffer().getBytes(loggedEvent.getValueOffset(), valueBuffer, 0, loggedEvent.getValueLength());

        assertThat(loggedEvent.getValueLength()).isEqualTo(JOB_EVENT.length);
        assertThat(valueBuffer).isEqualTo(JOB_EVENT);

        final RecordMetadata eventMetadata = new RecordMetadata();
        loggedEvent.readMetadata(eventMetadata);

        assertThat(eventMetadata.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(eventMetadata.getRequestStreamId()).isEqualTo(DEFAULT_ADDRESS.getStreamId());
    }

    @Test
    public void shouldWriteCommandRequestProtocolVersion() throws InterruptedException, ExecutionException
    {
        // given
        final short clientProtocolVersion = Protocol.PROTOCOL_VERSION - 1;
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_PARTITION_ID, clientProtocolVersion, ValueType.JOB, JobIntent.CREATE);

        // when
        final boolean isHandled = messageHandler.onRequest(serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, 123);

        // then
        assertThat(isHandled).isTrue();

        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream, true);
        waitForAvailableEvent(logStreamReader);

        final LoggedEvent loggedEvent = logStreamReader.next();
        final RecordMetadata eventMetadata = new RecordMetadata();
        loggedEvent.readMetadata(eventMetadata);

        assertThat(eventMetadata.getProtocolVersion()).isEqualTo(clientProtocolVersion);
    }

    @Test
    public void shouldWriteCommandRequestEventType() throws InterruptedException, ExecutionException
    {
        // given
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_PARTITION_ID, null, ValueType.JOB, JobIntent.CREATE);

        // when
        final boolean isHandled = messageHandler.onRequest(serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, 123);

        // then
        assertThat(isHandled).isTrue();

        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream, true);
        waitForAvailableEvent(logStreamReader);

        final LoggedEvent loggedEvent = logStreamReader.next();
        final RecordMetadata eventMetadata = new RecordMetadata();
        loggedEvent.readMetadata(eventMetadata);

        assertThat(eventMetadata.getValueType()).isEqualTo(ValueType.JOB);
        assertThat(eventMetadata.getIntent()).isEqualTo(JobIntent.CREATE);
    }

    @Test
    public void shouldHandleControlRequest()
    {
        // given
        final int writtenLength = writeControlRequestToBuffer(buffer);

        when(mockControlMessageDispatcher.claim(any(ClaimedFragment.class), anyInt())).thenAnswer(claimFragment(0));

        // when
        final boolean isHandled = messageHandler.onRequest(serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);

        // then
        assertThat(isHandled).isTrue();

        verify(mockControlMessageDispatcher).claim(any(ClaimedFragment.class), anyInt());

        int offset = fragmentOffset;

        controlMessageRequestHeaderDescriptor.wrap(sendBuffer, offset);

        assertThat(controlMessageRequestHeaderDescriptor.streamId()).isEqualTo(DEFAULT_ADDRESS.getStreamId());
        assertThat(controlMessageRequestHeaderDescriptor.requestId()).isEqualTo(REQUEST_ID);

        offset += ControlMessageRequestHeaderDescriptor.headerLength();

        headerEncoder.wrap(sendBuffer, offset);

        offset += headerEncoder.encodedLength();

        controlRequestDecoder.wrap(sendBuffer, offset, controlRequestDecoder.sbeBlockLength(), controlRequestDecoder.sbeSchemaVersion());

        final byte[] requestData = readBytes(controlRequestDecoder::getData, controlRequestDecoder::dataLength);

        assertThat(requestData).isEqualTo(JOB_EVENT);
    }

    @Test
    public void shouldSendErrorMessageIfTopicNotFound()
    {
        // given
        final int writtenLength = writeCommandRequestToBuffer(buffer, 99, null, ValueType.JOB, JobIntent.CREATE);

        // when
        final boolean isHandled = messageHandler.onRequest(serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);

        // then
        assertThat(isHandled).isTrue();

        final List<DirectBuffer> sentResponses = serverOutput.getSentResponses();
        assertThat(sentResponses).hasSize(1);

        final ErrorResponseDecoder errorDecoder = serverOutput.getAsErrorResponse(0);

        assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.PARTITION_NOT_FOUND);
        assertThat(errorDecoder.errorData()).isEqualTo("Cannot execute command. Partition with id '99' not found");
    }

    @Test
    public void shouldNotHandleUnkownRequest() throws InterruptedException, ExecutionException
    {
        // given
        headerEncoder.wrap(buffer, 0)
            .blockLength(commandRequestEncoder.sbeBlockLength())
            .schemaId(commandRequestEncoder.sbeSchemaId())
            .templateId(999)
            .version(1);

        // when
        final boolean isHandled = messageHandler.onRequest(serverOutput, DEFAULT_ADDRESS, buffer, 0, headerEncoder.encodedLength(), REQUEST_ID);

        // then
        assertThat(isHandled).isTrue();

        assertThat(serverOutput.getSentResponses()).hasSize(1);

        final ErrorResponseDecoder errorDecoder = serverOutput.getAsErrorResponse(0);

        assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.MESSAGE_NOT_SUPPORTED);
        assertThat(errorDecoder.errorData()).isEqualTo("Cannot handle message. Template id '999' is not supported.");
    }

    @Test
    public void shouldSendErrorMessageOnRequestWithNewerProtocolVersion()
    {
        // given
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_PARTITION_ID, Short.MAX_VALUE, ValueType.JOB, JobIntent.CREATE);

        // when
        final boolean isHandled = messageHandler.onRequest(serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);

        // then
        assertThat(isHandled).isTrue();

        assertThat(serverOutput.getSentResponses()).hasSize(1);

        final ErrorResponseDecoder errorDecoder = serverOutput.getAsErrorResponse(0);

        assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.INVALID_CLIENT_VERSION);
        assertThat(errorDecoder.errorData()).isEqualTo(String.format("Client has newer version than broker (%d > %d)", (int) Short.MAX_VALUE, ExecuteCommandRequestEncoder.SCHEMA_VERSION));
    }

    @Test
    public void shouldSendErrorMessageOnInvalidRequest() throws InterruptedException, ExecutionException
    {
        // given
        // request is invalid because Value type DEPLOYMENT does not match value contents, i.e. required values are not present
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_PARTITION_ID, null, ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

        // when
        final boolean isHandled = messageHandler.onRequest(serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);

        // then
        assertThat(isHandled).isTrue();

        assertThat(serverOutput.getSentResponses()).hasSize(1);

        final ErrorResponseDecoder errorDecoder = serverOutput.getAsErrorResponse(0);

        assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.INVALID_MESSAGE);
        assertThat(errorDecoder.errorData())
            .contains("Cannot deserialize command:")
            .contains("Property 'topicName' has no valid value");
    }

    @Test
    public void shouldSendErrorMessageOnUnsupportedRequest() throws InterruptedException, ExecutionException
    {
        // given
        final int writtenLength = writeCommandRequestToBuffer(buffer, LOG_STREAM_PARTITION_ID, null, ValueType.SBE_UNKNOWN, Intent.UNKNOWN);

        // when
        final boolean isHandled = messageHandler.onRequest(serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);

        // then
        assertThat(isHandled).isTrue();

        assertThat(serverOutput.getSentResponses()).hasSize(1);

        final ErrorResponseDecoder errorDecoder = serverOutput.getAsErrorResponse(0);

        assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.MESSAGE_NOT_SUPPORTED);
        assertThat(errorDecoder.errorData()).isEqualTo("Cannot execute command. Invalid event type 'NULL_VAL'.");
    }

    protected int writeCommandRequestToBuffer(UnsafeBuffer buffer, int partitionId, Short protocolVersion, ValueType type, Intent intent)
    {
        int offset = 0;

        final int protocolVersionToWrite = protocolVersion != null ? protocolVersion : commandRequestEncoder.sbeSchemaVersion();
        final ValueType eventTypeToWrite = type != null ? type : ValueType.NULL_VAL;

        headerEncoder.wrap(buffer, offset)
            .blockLength(commandRequestEncoder.sbeBlockLength())
            .schemaId(commandRequestEncoder.sbeSchemaId())
            .templateId(commandRequestEncoder.sbeTemplateId())
            .version(protocolVersionToWrite);

        offset += headerEncoder.encodedLength();

        commandRequestEncoder.wrap(buffer, offset);

        commandRequestEncoder
            .partitionId(partitionId)
            .valueType(eventTypeToWrite)
            .intent(intent.value())
            .putValue(JOB_EVENT, 0, JOB_EVENT.length);

        return headerEncoder.encodedLength() +
                commandRequestEncoder.encodedLength();
    }

    private int writeControlRequestToBuffer(UnsafeBuffer buffer)
    {
        int offset = 0;

        headerEncoder.wrap(buffer, offset)
            .blockLength(controlRequestEncoder.sbeBlockLength())
            .schemaId(controlRequestEncoder.sbeSchemaId())
            .templateId(controlRequestEncoder.sbeTemplateId())
            .version(controlRequestEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        controlRequestEncoder.wrap(buffer, offset);

        controlRequestEncoder.putData(JOB_EVENT, 0, JOB_EVENT.length);

        return headerEncoder.encodedLength() +
                controlRequestEncoder.encodedLength();
    }

    protected Answer<?> claimFragment(final long offset)
    {
        return invocation ->
        {
            final ClaimedFragment claimedFragment = (ClaimedFragment) invocation.getArguments()[0];
            final int length = (int) invocation.getArguments()[1];

            fragmentOffset = claimedFragment.getOffset();

            claimedFragment.wrap(sendBuffer, 0, alignedFramedLength(length), () ->
            { });

            final long claimedPosition = offset + alignedFramedLength(length);
            return claimedPosition;
        };
    }

    protected void waitForAvailableEvent(BufferedLogStreamReader logStreamReader)
    {
        TestUtil.waitUntil(() -> logStreamReader.hasNext());
    }

}
