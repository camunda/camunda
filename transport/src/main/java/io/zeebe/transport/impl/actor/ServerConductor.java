package io.zeebe.transport.impl.actor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.selector.AcceptTransportPoller;

public class ServerConductor extends Conductor
{
    private final AcceptTransportPoller acceptTransportPoller;

    public ServerConductor(ServerActorContext actorContext, TransportContext context)
    {
        super(actorContext, context);
        acceptTransportPoller = new AcceptTransportPoller(actorContext);
        acceptTransportPoller.addServerSocketBinding(context.getServerSocketBinding());
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = super.doWork();

        workCount += acceptTransportPoller.pollNow();

        return workCount;
    }


    public void onServerChannelOpenend(SocketChannel serverChannel)
    {
        deferred.runAsync(() ->
        {
            SocketAddress socketAddress = null;

            try
            {
                socketAddress = new SocketAddress((InetSocketAddress) serverChannel.getRemoteAddress());
            }
            catch (IOException e)
            {
                try
                {
                    serverChannel.close();
                }
                catch (IOException e1)
                {
                    return;
                }
            }

            RemoteAddress remoteAddress = remoteAddressList.getByAddress(socketAddress);

            if (remoteAddress == null)
            {
                remoteAddress = remoteAddressList.register(socketAddress);
            }

            final TransportChannel ch = new TransportChannel(this,
                remoteAddress,
                transportContext.getMessageMaxLength(),
                transportContext.getReceiveHandler(),
                null,
                serverChannel);

            onChannelConnected(ch);
        });
    }

}
