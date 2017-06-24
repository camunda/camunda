package io.zeebe.transport.impl.actor;

import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.selector.ReadTransportPoller;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;

public class Receiver implements Actor
{
    protected final DeferredCommandContext commandContext;
    protected final ReadTransportPoller transportPoller;

    public Receiver(ActorContext actorContext, TransportContext context)
    {
        this.commandContext = new DeferredCommandContext();
        this.transportPoller = new ReadTransportPoller();

        actorContext.setReceiver(this);
    }

    @Override
    public int doWork() throws Exception
    {
        int work = 0;

        work += commandContext.doWork();
        work += transportPoller.pollNow();

        return work;
    }

    public void closeSelectors()
    {
        transportPoller.close();
    }

    @Override
    public String name()
    {
        return "receiver";
    }

    public void removeChannel(TransportChannel c)
    {
        commandContext.runAsync(() ->
        {
            transportPoller.removeChannel(c);
        });
    }

    public void registerChannel(TransportChannel c)
    {
        commandContext.runAsync((future) ->
        {
            transportPoller.addChannel(c);
        });
    }

}
