package io.zeebe.broker.transport.clientapi;

import static io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder.topicNameHeaderLength;
import static io.zeebe.transport.protocol.Protocols.FULL_DUPLEX_SINGLE_MESSAGE;
import static io.zeebe.transport.protocol.Protocols.REQUEST_RESPONSE;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.broker.Constants;
import io.zeebe.broker.event.processor.TopicSubscriptionService;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.transport.controlmessage.ControlMessageRequestHeaderDescriptor;
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.protocol.clientapi.ControlMessageRequestDecoder;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.transport.Channel;
import io.zeebe.transport.protocol.TransportHeaderDescriptor;
import io.zeebe.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import io.zeebe.transport.singlemessage.SingleMessageHeaderDescriptor;


public class ClientApiMessageHandler
{

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    protected final RequestResponseProtocolHeaderDescriptor requestResponseProtocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();
    protected final ExecuteCommandRequestDecoder executeCommandRequestDecoder = new ExecuteCommandRequestDecoder();
    protected final ControlMessageRequestHeaderDescriptor controlMessageRequestHeaderDescriptor = new ControlMessageRequestHeaderDescriptor();

    protected final UnsafeBuffer writeControlMessageRequestBufferView = new UnsafeBuffer(0, 0);

    protected final DirectBuffer topicName = new UnsafeBuffer(0, 0);

    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected final Consumer<Runnable> cmdConsumer = (c) -> c.run();

    protected final Map<DirectBuffer, Int2ObjectHashMap<LogStream>> logStreamsByTopic = new HashMap<>();
    protected final BrokerEventMetadata eventMetadata = new BrokerEventMetadata();
    protected final LogStreamWriter logStreamWriter = new LogStreamWriterImpl();

    protected final Dispatcher sendBuffer;
    protected final ErrorResponseWriter errorResponseWriter;
    protected final Dispatcher controlMessageDispatcher;
    protected final ClaimedFragment claimedControlMessageFragment = new ClaimedFragment();

    protected final TopicSubscriptionService topicSubscriptionService;
    protected final TaskSubscriptionManager taskSubscriptionManager;

    public ClientApiMessageHandler(
        final Dispatcher sendBuffer,
        final Dispatcher controlMessageDispatcher,
        final ErrorResponseWriter errorResponseWriter,
        final TopicSubscriptionService topicSubscriptionService,
        final TaskSubscriptionManager taskSubscriptionManager)
    {
        this.sendBuffer = sendBuffer;
        this.controlMessageDispatcher = controlMessageDispatcher;
        this.errorResponseWriter = errorResponseWriter;
        this.topicSubscriptionService = topicSubscriptionService;
        this.taskSubscriptionManager = taskSubscriptionManager;
    }

    public boolean handleMessage(final Channel transportChannel, final DirectBuffer buffer, final int offset, final int length)
    {
        boolean isHandled = false;

        cmdQueue.drain(cmdConsumer);

        eventMetadata.reset();
        eventMetadata.reqChannelId(transportChannel.getStreamId());

        int messageOffset = offset + TransportHeaderDescriptor.headerLength();
        int messageLength = length - TransportHeaderDescriptor.headerLength();

        transportHeaderDescriptor.wrap(buffer, offset);

        final int protocol = transportHeaderDescriptor.protocolId();
        if (protocol == REQUEST_RESPONSE)
        {
            requestResponseProtocolHeaderDescriptor.wrap(buffer, messageOffset);

            eventMetadata.reqConnectionId(requestResponseProtocolHeaderDescriptor.connectionId());
            eventMetadata.reqRequestId(requestResponseProtocolHeaderDescriptor.requestId());
            messageOffset += RequestResponseProtocolHeaderDescriptor.headerLength();
            messageLength -= RequestResponseProtocolHeaderDescriptor.headerLength();
        }
        else if (protocol == FULL_DUPLEX_SINGLE_MESSAGE)
        {
            messageOffset += SingleMessageHeaderDescriptor.HEADER_LENGTH;
            messageLength -= SingleMessageHeaderDescriptor.HEADER_LENGTH;
        }

        messageHeaderDecoder.wrap(buffer, messageOffset);

        final int templateId = messageHeaderDecoder.templateId();
        final int clientVersion = messageHeaderDecoder.version();

        if (clientVersion > Constants.PROTOCOL_VERSION)
        {
            return errorResponseWriter
                        .metadata(eventMetadata)
                        .errorCode(ErrorCode.INVALID_CLIENT_VERSION)
                        .errorMessage("Client has newer version than broker (%d > %d)", clientVersion, Constants.PROTOCOL_VERSION)
                        .failedRequest(buffer, messageOffset, messageLength)
                        .tryWriteResponseOrLogFailure();
        }

        eventMetadata.protocolVersion(clientVersion);

        switch (templateId)
        {
            case ExecuteCommandRequestDecoder.TEMPLATE_ID:

                isHandled = handleExecuteCommandRequest(eventMetadata, buffer, messageOffset, messageLength);
                break;

            case ControlMessageRequestDecoder.TEMPLATE_ID:

                isHandled = handleControlMessageRequest(eventMetadata, buffer, messageOffset, messageLength);
                break;

            default:
                isHandled = errorResponseWriter
                        .metadata(eventMetadata)
                        .errorCode(ErrorCode.MESSAGE_NOT_SUPPORTED)
                        .errorMessage("Cannot handle message. Template id '%d' is not supported.", templateId)
                        .failedRequest(buffer, messageOffset, messageLength)
                        .tryWriteResponseOrLogFailure();
                break;
        }

        return isHandled;
    }

    protected boolean handleExecuteCommandRequest(final BrokerEventMetadata eventMetadata, final DirectBuffer buffer, final int messageOffset, final int messageLength)
    {
        boolean isHandled = false;

        executeCommandRequestDecoder.wrap(buffer, messageOffset + messageHeaderDecoder.encodedLength(), messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

        final int topicNameOffset = executeCommandRequestDecoder.limit() + topicNameHeaderLength();
        final int topicNameLength = executeCommandRequestDecoder.topicNameLength();
        topicName.wrap(buffer, topicNameOffset, topicNameLength);
        executeCommandRequestDecoder.limit(topicNameOffset + topicNameLength);

        final int partitionId = executeCommandRequestDecoder.partitionId();
        final long key = executeCommandRequestDecoder.key();

        final EventType eventType = executeCommandRequestDecoder.eventType();
        eventMetadata.eventType(eventType);

        final int eventOffset = executeCommandRequestDecoder.limit() + ExecuteCommandRequestDecoder.commandHeaderLength();
        final int eventLength = executeCommandRequestDecoder.commandLength();

        final LogStream logStream = getLogStream(topicName, partitionId);

        if (logStream != null)
        {
            eventMetadata.raftTermId(logStream.getTerm());
            logStreamWriter.wrap(logStream);

            if (key != ExecuteCommandRequestDecoder.keyNullValue())
            {
                logStreamWriter.key(key);
            }
            else
            {
                logStreamWriter.positionAsKey();
            }

            final long eventPosition = logStreamWriter
                .metadataWriter(eventMetadata)
                .value(buffer, eventOffset, eventLength)
                .tryWrite();

            isHandled = eventPosition >= 0;
        }
        else
        {
            isHandled = errorResponseWriter
                    .metadata(eventMetadata)
                    .errorCode(ErrorCode.TOPIC_NOT_FOUND)
                    .errorMessage("Cannot execute command. Topic with name '%s' and partition id '%d' not found", bufferAsString(topicName), partitionId)
                    .failedRequest(buffer, messageOffset, messageLength)
                    .tryWriteResponseOrLogFailure();
        }
        return isHandled;
    }

    private LogStream getLogStream(final DirectBuffer topicName, final int partitionId)
    {
        final Int2ObjectHashMap<LogStream> logStreamPartitions = logStreamsByTopic.get(topicName);

        if (logStreamPartitions != null)
        {
            return logStreamPartitions.get(partitionId);
        }

        return null;
    }

    protected boolean handleControlMessageRequest(final BrokerEventMetadata eventMetadata, final DirectBuffer buffer, final int messageOffset, final int messageLength)
    {
        boolean isHandled = false;
        long publishPosition = -1;

        do
        {
            publishPosition = controlMessageDispatcher.claim(claimedControlMessageFragment, ControlMessageRequestHeaderDescriptor.framedLength(messageLength));
        }
        while (publishPosition == -2);

        if (publishPosition >= 0)
        {
            final MutableDirectBuffer writeBuffer = claimedControlMessageFragment.getBuffer();
            int writeBufferOffset = claimedControlMessageFragment.getOffset();

            controlMessageRequestHeaderDescriptor
                .wrap(writeBuffer, writeBufferOffset)
                .channelId(eventMetadata.getReqChannelId())
                .connectionId(eventMetadata.getReqConnectionId())
                .requestId(eventMetadata.getReqRequestId());

            writeBufferOffset += ControlMessageRequestHeaderDescriptor.headerLength();

            writeBuffer.putBytes(writeBufferOffset, buffer, messageOffset, messageLength);

            claimedControlMessageFragment.commit();

            isHandled = true;
        }

        return isHandled;
    }

    public void addStream(final LogStream logStream)
    {
        cmdQueue.add(() ->
            logStreamsByTopic
                .computeIfAbsent(logStream.getTopicName(), topicName -> new Int2ObjectHashMap<>())
                .put(logStream.getPartitionId(), logStream)
        );
    }

    public void removeStream(final LogStream logStream)
    {
        cmdQueue.add(() ->
        {
            final DirectBuffer topicName = logStream.getTopicName();
            final int partitionId = logStream.getPartitionId();

            final Int2ObjectHashMap<LogStream> logStreamPartitions = logStreamsByTopic.get(topicName);

            if (logStreamPartitions != null)
            {
                logStreamPartitions.remove(partitionId);

                if (logStreamPartitions.isEmpty())
                {
                    logStreamsByTopic.remove(topicName);
                }
            }
        });
    }

    public void onChannelClose(final int channelId)
    {
        topicSubscriptionService.onClientChannelCloseAsync(channelId);
        taskSubscriptionManager.onClientChannelCloseAsync(channelId);
    }

}
