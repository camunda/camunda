package org.camunda.tngp.broker.transport.clientapi;

import static org.camunda.tngp.transport.protocol.Protocols.*;

import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.broker.logstreams.requests.LogStreamRequest;
import org.camunda.tngp.broker.logstreams.requests.LogStreamRequestManager;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreamWriter;
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
        final int protocol = buffer.getInt(TransportHeaderDescriptor.protocolIdOffset(offset));

        if (protocol == REQUEST_RESPONSE)
        {
            messageOffset += RequestResponseProtocolHeaderDescriptor.headerLength();
        }
        else if (protocol == FULL_DUPLEX_SINGLE_MESSAGE)
        {
            messageOffset += SingleMessageHeaderDescriptor.HEADER_LENGTH;
        }

        messageHeaderDecoder.wrap(buffer, messageOffset);
        final int templateId = messageHeaderDecoder.templateId();

        if (100 <= templateId && templateId <= 199)
        {
            isHandled = handleDataMessage(transportChannel, protocol, templateId, buffer, offset, messageOffset);
        }
        else if (1 <= templateId && templateId <= 99)
        {
            isHandled = handleControlMessage(templateId, transportChannel, buffer, offset, length);
        }
        else
        {
            // TODO: error, unrecognized message
        }

        return isHandled;
    }

    private boolean handleDataMessage(TransportChannel transportChannel, int templateId, int protocol, DirectBuffer buffer, int offset, int messageOffset)
    {
        boolean isHandled = false;

        if (executeCommandRequestDecoder.sbeTemplateId() == templateId)
        {
            executeCommandRequestDecoder.wrap(buffer, messageOffset, messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

            final long topicId = executeCommandRequestDecoder.topicId();
            final LogStream logStream = logStreamsById.get(topicId);
            final LogStreamRequestManager requestQueue = null;

            if (logStream != null)
            {
                final LogStreamRequest request = requestQueue.open();

                if (request != null)
                {
                    logStreamWriter.wrap(logStream);

                    final long eventPosition = logStreamWriter
                        .positionAsKey()
                        .value(buffer, executeCommandRequestDecoder.limit(), executeCommandRequestDecoder.commandLength())
                        .tryWrite();

                    if (eventPosition >= 0)
                    {
                        request.setChannelId(transportChannel.getId());
                        request.setLogStreamPosition(eventPosition);

                        if (protocol == REQUEST_RESPONSE)
                        {
                            request.setConnectionId(buffer.getLong(RequestResponseProtocolHeaderDescriptor.connectionIdOffset(offset)));
                            request.setRequestId(buffer.getLong(RequestResponseProtocolHeaderDescriptor.requestIdOffset(offset)));
                        }

                        request.enqueue();
                        isHandled = true;
                    }
                    else
                    {
                        request.close();
                        isHandled = false;
                    }
                }
            }
            else
            {
                // TODO: send STREAM_NOT_FOUND error message
            }
        }
        else
        {
            // TODO: publish events
        }

        return isHandled;
    }

    private boolean handleControlMessage(int templateId, TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        long publishPosition = -1;

        do
        {
            publishPosition = controlMessageDispatcher.offer(buffer, offset, length, transportChannel.getId());
        }
        while (publishPosition == -2);

        return publishPosition >= 0;
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
