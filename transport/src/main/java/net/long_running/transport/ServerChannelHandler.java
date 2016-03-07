package net.long_running.transport;

@FunctionalInterface
public interface ServerChannelHandler
{
    void onChannelAccepted(ServerChannel serverChannel);
}
