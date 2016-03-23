package org.camunda.tngp.transport;

import uk.co.real_logic.agrona.DirectBuffer;

public interface ChannelReceiveHandler
{
    long onMessage(DirectBuffer buffer, int offset, int length, int channelId);
}
