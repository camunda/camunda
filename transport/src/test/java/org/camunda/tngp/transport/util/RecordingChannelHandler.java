package org.camunda.tngp.transport.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.Channel;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class RecordingChannelHandler implements TransportChannelHandler
{

    protected List<Channel> openInvocations = new CopyOnWriteArrayList<>();
    protected List<Channel> closeInvocations = new CopyOnWriteArrayList<>();
    protected List<Channel> sendErrorInvocations = new CopyOnWriteArrayList<>();
    protected List<Channel> receiveMessageInvocations = new CopyOnWriteArrayList<>();
    protected List<Channel> receiveControlFrameInvocations = new CopyOnWriteArrayList<>();
    protected List<Channel> keepAliveInvocations = new CopyOnWriteArrayList<>();

    public List<Channel> getChannelCloseInvocations()
    {
        return closeInvocations;
    }

    public List<Channel> getChannelOpenInvocations()
    {
        return openInvocations;
    }

    public List<Channel> getChannelReceiveControlFrameInvocations()
    {
        return receiveControlFrameInvocations;
    }

    public List<Channel> getChannelReceiveMessageInvocations()
    {
        return receiveMessageInvocations;
    }

    public List<Channel> getChannelSendErrorInvocations()
    {
        return sendErrorInvocations;
    }

    public List<Channel> getKeepAliveInvocations()
    {
        return keepAliveInvocations;
    }

    @Override
    public void onChannelOpened(Channel transportChannel)
    {
        openInvocations.add(transportChannel);
    }

    @Override
    public void onChannelClosed(Channel transportChannel)
    {
        closeInvocations.add(transportChannel);
    }

    @Override
    public void onChannelSendError(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        sendErrorInvocations.add(transportChannel);
    }

    @Override
    public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        receiveMessageInvocations.add(transportChannel);
        return true;
    }

    @Override
    public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        receiveControlFrameInvocations.add(transportChannel);
        return true;
    }

    @Override
    public void onChannelKeepAlive(Channel channel)
    {
        keepAliveInvocations.add(channel);
    }
}