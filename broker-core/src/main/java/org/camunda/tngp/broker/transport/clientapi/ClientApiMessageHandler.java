package org.camunda.tngp.broker.transport.clientapi;

import static org.camunda.tngp.transport.protocol.Protocols.FULL_DUPLEX_SINGLE_MESSAGE;
import static org.camunda.tngp.transport.protocol.Protocols.REQUEST_RESPONSE;

import java.util.function.Consumer;

import javax.swing.ButtonGroup;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.broker.logstreams.requests.LogStreamRequest;
import org.camunda.tngp.broker.logstreams.requests.LogStreamRequestManager;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.protocol.clientapi.ControlMessageRequestDecoder;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandRequestDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.protocol.clientapi.PublishEventsRequestDecoder;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.transport.singlemessage.SingleMessageHeaderDescriptor;

public class ClientApiMessageHandler
{
    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final ExecuteCommandRequestDecoder executeCommandRequestDecoder = new ExecuteCommandRequestDecoder();
    protected final PublishEventsRequestDecoder publishEventsRequestDecoder = new PublishEventsRequestDecoder();

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

        int messageOffset = offset + TransportHeaderDescriptor.headerLength();
        int messageLength = length - TransportHeaderDescriptor.headerLength();

        final int protocol = buffer.getInt(TransportHeaderDescriptor.protocolIdOffset(offset));
        if (protocol == REQUEST_RESPONSE)
        {
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
                final LogStream logStream = logStreamsById.get(topicId);

                if (logStream != null)
                {
                    logStreamWriter.wrap(logStream);

                    final long eventPosition = logStreamWriter
                        .positionAsKey()
                        .value(buffer, executeCommandRequestDecoder.limit(), executeCommandRequestDecoder.commandLength())
                        .tryWrite();

                    if (eventPosition >= 0)
                    {
                        if (protocol == REQUEST_RESPONSE)
                        {
                        }

                        isHandled = true;
                    }
                    else
                    {
                        isHandled = false;
                    }
                }
                else
                {
                    // TODO: send STREAM_NOT_FOUND error message
                }

                break;

            case PublishEventsRequestDecoder.TEMPLATE_ID:

                isHandled = handlePublishEventsRequest(transportChannel, buffer);

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

    public void addStream(LogStream logStream)
    {
        cmdQueue.add(() -> logStreamsById.put(logStream.getId(), logStream));
    }

    public void removeStream(LogStream logStream)
    {
        cmdQueue.add(() -> logStreamsById.remove(logStream.getId()));
    }
}
