package io.zeebe.transport.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.transport.ClientMessageHandler;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;

public class RecordingMessageHandler implements ServerMessageHandler, ClientMessageHandler
{

    protected List<ReceivedMessage> receivedMessages = new CopyOnWriteArrayList<>();

    @Override
    public boolean onMessage(
            ServerOutput output,
            RemoteAddress remoteAddress,
            DirectBuffer buffer,
            int offset,
            int length)
    {
        recordMessage(remoteAddress, buffer, offset, length);

        return true;
    }

    @Override
    public boolean onMessage(
            ClientOutput output,
            RemoteAddress remoteAddress,
            DirectBuffer buffer,
            int offset,
            int length)
    {
        recordMessage(remoteAddress, buffer, offset, length);

        return true;
    }

    private void recordMessage(RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length)
    {
        final ReceivedMessage msg = new ReceivedMessage();
        msg.remote = remoteAddress;
        msg.buffer = new UnsafeBuffer(new byte[length]);
        msg.buffer.putBytes(0, buffer, offset, length);

        receivedMessages.add(msg);
    }

    public ReceivedMessage getMessage(int index)
    {
        return receivedMessages.get(index);
    }

    public int numReceivedMessages()
    {
        return receivedMessages.size();
    }

    public static class ReceivedMessage
    {
        protected RemoteAddress remote;
        protected UnsafeBuffer buffer;

        public RemoteAddress getRemote()
        {
            return remote;
        }

        public UnsafeBuffer getBuffer()
        {
            return buffer;
        }
    }
}

