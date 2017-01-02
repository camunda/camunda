package org.camunda.tngp.broker.transport.clientapi;

import static org.camunda.tngp.transport.protocol.Protocols.FULL_DUPLEX_SINGLE_MESSAGE;
import static org.camunda.tngp.transport.protocol.Protocols.REQUEST_RESPONSE;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.protocol.clientapi.ControlMessageRequestDecoder;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.ErrorResponseEncoder;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandRequestDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.transport.singlemessage.SingleMessageHeaderDescriptor;

public class ClientApiMessageHandler
{
    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandRequestDecoder executeCommandRequestDecoder = new ExecuteCommandRequestDecoder();
    protected final ErrorResponseEncoder errorResponseEncoder = new ErrorResponseEncoder();
    protected final ClaimedFragment errorMessageBuffer = new ClaimedFragment();

    protected final BrokerEventMetadata eventMetadata = new BrokerEventMetadata();

    protected final Int2ObjectHashMap<LogStream> logStreamsById = new Int2ObjectHashMap<>();
    protected final LogStreamWriter logStreamWriter = new LogStreamWriter();
    protected final Dispatcher sendBuffer;
    protected final Dispatcher controlMessageDispatcher;
    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected final Consumer<Runnable> cmdConsumer = (c) -> c.run();

    public ClientApiMessageHandler(Dispatcher sendBuffer, Dispatcher controlMessageDispatcher)
    {
        this.sendBuffer = sendBuffer;
        this.controlMessageDispatcher = controlMessageDispatcher;
    }

    public boolean handleMessage(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        boolean isHandled = false;

        cmdQueue.drain(cmdConsumer);

        eventMetadata.reset();
        eventMetadata.reqChannelId(transportChannel.getId());

        int messageOffset = offset + TransportHeaderDescriptor.headerLength();
        int messageLength = length - TransportHeaderDescriptor.headerLength();

        final int protocol = buffer.getInt(TransportHeaderDescriptor.protocolIdOffset(offset));
        if (protocol == REQUEST_RESPONSE)
        {
            eventMetadata.reqConnectionId(RequestResponseProtocolHeaderDescriptor.connectionIdOffset(messageOffset));
            eventMetadata.reqRequestId(RequestResponseProtocolHeaderDescriptor.requestIdOffset(messageOffset));
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

        switch (templateId)
        {
            case ExecuteCommandRequestDecoder.TEMPLATE_ID:

                executeCommandRequestDecoder.wrap(buffer, messageOffset, messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

                final long topicId = executeCommandRequestDecoder.topicId();
                final int eventLength = executeCommandRequestDecoder.commandLength();
                final int eventOffset = executeCommandRequestDecoder.limit() + ExecuteCommandRequestDecoder.commandHeaderLength();

                final LogStream logStream = logStreamsById.get(topicId);

                if (logStream != null)
                {
                    // TODO: eventMetadata.raftTermId(logStream.getCurrentTermId());

                    logStreamWriter.wrap(logStream);

                    final long eventPosition = logStreamWriter
                        .positionAsKey()
                        .metadataWriter(eventMetadata)
                        .value(buffer, eventOffset, eventLength)
                        .tryWrite();

                    isHandled = eventPosition >= 0;
                }
                else
                {
                    final String errorMessage = String.format("Cannot execute command. Topic with id '%s' id not found", topicId);
                    isHandled = sendErrorMessage(ErrorCode.TOPIC_NOT_FOUND, errorMessage, buffer, messageOffset, messageLength);
                }

                break;

            case ControlMessageRequestDecoder.TEMPLATE_ID:

                long publishPosition = -1;

                do
                {
                    publishPosition = controlMessageDispatcher.offer(buffer, messageOffset, messageLength, transportChannel.getId());
                }
                while (publishPosition == -2);

                isHandled = publishPosition >= 0;

                break;

            default:

                break;
        }

        return isHandled;
    }

    protected boolean sendErrorMessage(
            ErrorCode errorCode,
            String errorMessage,
            DirectBuffer failedRequestBuffer,
            int failedRequestOffset,
            int failedRequestLength)
    {
        final byte[] errorBytes = errorMessage.getBytes(StandardCharsets.UTF_8);

        final int encodedLength = messageHeaderEncoder.encodedLength() +
                errorResponseEncoder.sbeBlockLength() +
                ErrorResponseEncoder.errorDataHeaderLength() +
                errorBytes.length +
                ErrorResponseEncoder.failedRequestHeaderLength() +
                failedRequestLength;

        boolean isPublished = false;

        if (sendBuffer.claim(errorMessageBuffer, encodedLength) >= 0)
        {
            try
            {
                errorResponseEncoder
                    .errorCode(errorCode)
                    .putErrorData(errorBytes, 0, errorBytes.length)
                    .putFailedRequest(failedRequestBuffer, failedRequestOffset, failedRequestLength);

                errorMessageBuffer.commit();

                isPublished = true;
            }
            catch (Throwable e)
            {
                errorMessageBuffer.abort();
                LangUtil.rethrowUnchecked(e);
            }
        }

        return isPublished;
    }

    public void addStream(LogStream logStream)
    {
        cmdQueue.add(() -> logStreamsById.put(logStream.getId(), logStream));
    }

    public void removeStream(LogStream logStream)
    {
        cmdQueue.add(() -> logStreamsById.remove(logStream.getId()));
    }
}
