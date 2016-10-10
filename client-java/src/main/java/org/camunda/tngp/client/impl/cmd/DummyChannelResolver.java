package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientChannelResolver;

public class DummyChannelResolver implements ClientChannelResolver
{
    protected int channelId = -1;

    @Override
    public int getChannelIdForCmd(final AbstractCmdImpl<?> cmd)
    {
        return resolveChannelId();
    }

    @Override
    public int getChannelIdForCmd(AbstractSingleMessageCmd cmd)
    {
        return resolveChannelId();
    }

    protected int resolveChannelId()
    {
        if (channelId == -1)
        {
            throw new RuntimeException("Not connected; call connect() on the client first.");
        }

        return channelId;
    }

    public void setChannelId(int channelId)
    {
        this.channelId = channelId;
    }

    public void resetChannelId()
    {
        this.channelId = -1;
    }


}
