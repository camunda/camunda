package org.camunda.tngp.broker.transport.clientapi;

import static org.camunda.tngp.transport.protocol.Protocols.*;

import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreamWriter;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.transport.singlemessage.SingleMessageHeaderDescriptor;

public class ClientApiMessageHandler
{
    protected final org.camunda.tngp.protocol.clientapi.data.MessageHeaderDecoder dataMessageHeaderDecoder = new org.camunda.tngp.protocol.clientapi.data.MessageHeaderDecoder();
    protected final org.camunda.tngp.protocol.clientapi.control.MessageHeaderDecoder controlMessageHeaderDecoder = new org.camunda.tngp.protocol.clientapi.control.MessageHeaderDecoder();

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

        controlMessageHeaderDecoder.wrap(buffer, messageOffset);
        final int schemaId = controlMessageHeaderDecoder.schemaId();

        if (schemaId == dataMessageHeaderDecoder.schemaId())
        {
            isHandled = handleDataMessage(transportChannel, buffer, offset, length, messageOffset);
        }
        else
        {
            isHandled = handleControlMessage(transportChannel, buffer, offset, length);
        }

        return isHandled;
    }

    private boolean handleDataMessage(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length, int messageOffset)
    {
        boolean isHandled = false;

        dataMessageHeaderDecoder.wrap(buffer, messageOffset);

        final int streamId = dataMessageHeaderDecoder.streamId();
        final LogStream logStream = logStreamsById.get(streamId);

        if (logStream != null)
        {
            logStreamWriter.wrap(logStream);

            final long eventPosition = logStreamWriter
                .positionAsKey()
                .value(buffer, messageOffset, length - messageOffset)
                .tryWrite();

            isHandled = eventPosition >= 0;
        }
        else
        {
            // TODO: send STREAM_NOT_FOUND error message
        }

        return isHandled;
    }

    private boolean handleControlMessage(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
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
