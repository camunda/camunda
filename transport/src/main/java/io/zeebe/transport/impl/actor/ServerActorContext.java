package io.zeebe.transport.impl.actor;

import java.nio.channels.SocketChannel;

public class ServerActorContext extends ActorContext
{
    private ServerConductor serverConductor;

    public void onServerChannelOpened(SocketChannel serverChannel)
    {
        serverConductor.onServerChannelOpenend(serverChannel);
    }

    @Override
    public void setConductor(Conductor conductor)
    {
        super.setConductor(conductor);
        this.serverConductor = (ServerConductor) conductor;
    }
}
