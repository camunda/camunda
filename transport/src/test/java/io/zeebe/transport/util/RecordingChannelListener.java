package io.zeebe.transport.util;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.transport.Channel;
import io.zeebe.transport.TransportChannelListener;

public class RecordingChannelListener implements TransportChannelListener
{

    protected List<Channel> closedChannels = new ArrayList<>();
    protected List<Channel> interruptedChannels = new ArrayList<>();
    protected List<Channel> openedChannels = new ArrayList<>();

    @Override
    public void onChannelClosed(Channel channel)
    {
        closedChannels.add(channel);
    }

    public List<Channel> getClosedChannels()
    {
        return closedChannels;
    }

    @Override
    public void onChannelInterrupted(Channel channel)
    {
        interruptedChannels.add(channel);
    }

    public List<Channel> getInterruptedChannels()
    {
        return interruptedChannels;
    }

    @Override
    public void onChannelOpened(Channel channel)
    {
        openedChannels.add(channel);
    }

    public List<Channel> getOpenedChannels()
    {
        return openedChannels;
    }

}