package io.zeebe.broker.transport.clientapi;

import static io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder.topicNameHeaderLength;
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
import io.zeebe.broker.logstreams.BrokerEventMetadata;
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
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;


public class ClientApiMessageHandler implements ServerMessageHandler, ServerRequestHandler
{

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final ExecuteCommandRequestDecoder executeCommandRequestDecoder = new ExecuteCommandRequestDecoder();
    protected final ControlMessageRequestHeaderDescriptor controlMessageRequestHeaderDescriptor = new ControlMessageRequestHeaderDescriptor();

    protected final DirectBuffer topicName = new UnsafeBuffer(0, 0);

    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected final Consumer<Runnable> cmdConsumer = (c) -> c.run();

    protected final Map<DirectBuffer, Int2ObjectHashMap<LogStream>> logStreamsByTopic = new HashMap<>();
    protected final BrokerEventMetadata eventMetadata = new BrokerEventMetadata();
    protected final LogStreamWriter logStreamWriter = new LogStreamWriterImpl();

    protected final ErrorResponseWriter errorResponseWriter = new ErrorResponseWriter();
    protected final Dispatcher controlMessageDispatcher;
    protected final ClaimedFragment claimedControlMessageFragment = new ClaimedFragment();

    public ClientApiMessageHandler(final Dispatcher controlMessageDispatcher)
    {
        this.controlMessageDispatcher = controlMessageDispatcher;
    }

//    public boolean handleMessage(final Channel transportChannel, final DirectBuffer buffer, final int offset, final int length)
//    {
//        boolean isHandled = false;
//
//        cmdQueue.drain(cmdConsumer);
//
//        eventMetadata.reset();
//        eventMetadata.reqChannelId(transportChannel.getStreamId());
//
//        int messageOffset = offset + TransportHeaderDescriptor.headerLength();
//        int messageLength = length - TransportHeaderDescriptor.headerLength();
//
//        transportHeaderDescriptor.wrap(buffer, offset);
//
//        final int protocol = transportHeaderDescriptor.protocolId();
//        if (protocol == REQUEST_RESPONSE)
//        {
//            requestResponseProtocolHeaderDescriptor.wrap(buffer, messageOffset);
//
//            eventMetadata.reqConnectionId(requestResponseProtocolHeaderDescriptor.connectionId());
//            eventMetadata.reqRequestId(requestResponseProtocolHeaderDescriptor.requestId());
//            messageOffset += RequestResponseProtocolHeaderDescriptor.headerLength();
//            messageLength -= RequestResponseProtocolHeaderDescriptor.headerLength();
//        }
//        else if (protocol == FULL_DUPLEX_SINGLE_MESSAGE)
//        {
//            messageOffset += SingleMessageHeaderDescriptor.HEADER_LENGTH;
//            messageLength -= SingleMessageHeaderDescriptor.HEADER_LENGTH;
//        }
//
//        messageHeaderDecoder.wrap(buffer, messageOffset);
//
//        final int templateId = messageHeaderDecoder.templateId();
//        final int clientVersion = messageHeaderDecoder.version();
//
//        if (clientVersion > Constants.PROTOCOL_VERSION)
//        {
//            return errorResponseWriter
//                        .metadata(eventMetadata)
//                        .errorCode(ErrorCode.INVALID_CLIENT_VERSION)
//                        .errorMessage("Client has newer version than broker (%d > %d)", clientVersion, Constants.PROTOCOL_VERSION)
//                        .failedRequest(buffer, messageOffset, messageLength)
//                        .tryWriteResponseOrLogFailure();
//        }
//
//        eventMetadata.protocolVersion(clientVersion);
//
//        switch (templateId)
//        {
//            case ExecuteCommandRequestDecoder.TEMPLATE_ID:
//
//                isHandled = handleExecuteCommandRequest(
//                        output,
//
//                        eventMetadata,
//                        buffer,
//                        messageOffset,
//                        messageLength);
//                break;
//
//            case ControlMessageRequestDecoder.TEMPLATE_ID:
//
//                isHandled = handleControlMessageRequest(eventMetadata, buffer, messageOffset, messageLength);
//                break;
//
//            default:
//                isHandled = errorResponseWriter
//                        .metadata(eventMetadata)
//                        .errorCode(ErrorCode.MESSAGE_NOT_SUPPORTED)
//                        .errorMessage("Cannot handle message. Template id '%d' is not supported.", templateId)
//                        .failedRequest(buffer, messageOffset, messageLength)
//                        .tryWriteResponseOrLogFailure();
//                break;
//        }
//
//        return isHandled;
//    }

    protected boolean handleExecuteCommandRequest(
            final ServerOutput output,
            final RemoteAddress requestAddress,
            final long requestId,
            final BrokerEventMetadata eventMetadata,
            final DirectBuffer buffer,
            final int messageOffset,
            final int messageLength)
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
                    .errorCode(ErrorCode.TOPIC_NOT_FOUND)
                    .errorMessage("Cannot execute command. Topic with name '%s' and partition id '%d' not found", bufferAsString(topicName), partitionId)
                    .failedRequest(buffer, messageOffset, messageLength)
                    .tryWriteResponseOrLogFailure(output, requestAddress.getStreamId(), requestId);
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

    protected boolean handleControlMessageRequest(
            final BrokerEventMetadata eventMetadata,
            final DirectBuffer buffer,
            final int messageOffset,
            final int messageLength)
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
                .streamId(eventMetadata.getRequestStreamId())
                .requestId(eventMetadata.getRequestId());

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

    @Override
    public boolean onRequest(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length, long requestId)
    {
        drainCommandQueue();

        messageHeaderDecoder.wrap(buffer, offset);

        final int templateId = messageHeaderDecoder.templateId();
        final int clientVersion = messageHeaderDecoder.version();


        if (clientVersion > Constants.PROTOCOL_VERSION)
        {
            return errorResponseWriter
                .errorCode(ErrorCode.INVALID_CLIENT_VERSION)
                .errorMessage("Client has newer version than broker (%d > %d)", clientVersion, Constants.PROTOCOL_VERSION)
                .failedRequest(buffer, offset, length)
                .tryWriteResponse(output, remoteAddress.getStreamId(), requestId);
        }

        eventMetadata.reset();
        eventMetadata.protocolVersion(clientVersion);
        eventMetadata.requestId(requestId);
        eventMetadata.requestStreamId(remoteAddress.getStreamId());

        final boolean isHandled;
        switch (templateId)
        {
            case ExecuteCommandRequestDecoder.TEMPLATE_ID:

                isHandled = handleExecuteCommandRequest(
                        output,
                        remoteAddress,
                        requestId,
                        eventMetadata,
                        buffer,
                        offset,
                        length);
                break;

            case ControlMessageRequestDecoder.TEMPLATE_ID:
                isHandled = handleControlMessageRequest(eventMetadata, buffer, offset, length);
                break;

            default:
                isHandled = errorResponseWriter
                        .errorCode(ErrorCode.MESSAGE_NOT_SUPPORTED)
                        .errorMessage("Cannot handle message. Template id '%d' is not supported.", templateId)
                        .failedRequest(buffer, offset, length)
                        .tryWriteResponse(output, remoteAddress.getStreamId(), requestId);
                break;
        }

        return isHandled;
    }

    @Override
    public boolean onMessage(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset,
            int length)
    {
        // ignore; currently no incoming single-message client interactions
        return true;
    }

    protected void drainCommandQueue()
    {
        cmdQueue.drain(cmdConsumer);
    }

}
