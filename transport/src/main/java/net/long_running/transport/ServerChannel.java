package net.long_running.transport;

public interface ServerChannel extends BaseChannel
{
    void setChannelFrameHandler(ChannelFrameHandler frameHandler);
}
