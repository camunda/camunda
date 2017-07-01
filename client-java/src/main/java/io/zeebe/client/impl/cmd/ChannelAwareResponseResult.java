package io.zeebe.client.impl.cmd;

import io.zeebe.transport.Channel;

public interface ChannelAwareResponseResult
{

    void setChannel(Channel channel);

}
