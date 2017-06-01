package org.camunda.tngp.transport.util;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.transport.Channel;
import org.camunda.tngp.transport.TransportChannelListener;

public class RecordingChannelListener implements TransportChannelListener
{

    protected List<Channel> closedChannels = new ArrayList<>();

    @Override
    public void onChannelClosed(Channel channel)
    {
        closedChannels.add(channel);
    }

    public List<Channel> getClosedChannels()
    {
        return closedChannels;
    }

}