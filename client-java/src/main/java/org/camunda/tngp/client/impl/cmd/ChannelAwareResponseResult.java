package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.transport.Channel;

public interface ChannelAwareResponseResult
{

    void setChannel(Channel channel);

}
