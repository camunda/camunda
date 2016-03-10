package org.camunda.tngp.transport;

public interface ServerChannel extends BaseChannel
{
    void setChannelFrameHandler(ChannelFrameHandler frameHandler);
}
