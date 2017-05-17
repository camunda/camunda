package org.camunda.tngp.transport.impl.agent;

import java.util.concurrent.CompletableFuture;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.transport.impl.ChannelImpl;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.impl.media.ReadTransportPoller;
import org.camunda.tngp.util.DeferredCommandContext;

/**
 *
 * Responsibilities
 * <ul>
 * <li>Polling the media for READ and performing {@link ChannelImpl#receive()} operations</li>
 * <li>Processing Request timeouts</li>
 * <li>Closing connections</li>
 * <li>Closing channels</li>
 * </ul>
 */
public class Receiver implements Agent
{
    protected final DeferredCommandContext commandContext;
    protected final ReadTransportPoller transportPoller;

    public Receiver(TransportContext context)
    {
        commandContext = new DeferredCommandContext();
        transportPoller = new ReadTransportPoller();
    }

    public int doWork() throws Exception
    {
        int work = 0;

        work += commandContext.doWork();
        work += transportPoller.pollNow();

        return work;
    }

    @Override
    public void onClose()
    {
        transportPoller.close();
    }

    public String roleName()
    {
        return "receiver";
    }

    public CompletableFuture<Void> removeChannelAsync(ChannelImpl c)
    {
        return commandContext.runAsync((future) ->
        {
            transportPoller.removeChannel(c);
            future.complete(null);
        });
    }

    public CompletableFuture<Void> registerChannelAsync(ChannelImpl c)
    {
        return commandContext.runAsync((future) ->
        {
            try
            {
                transportPoller.addChannel(c);
                future.complete(null);
            }
            catch (Exception e)
            {
                future.completeExceptionally(e);
            }
        });
    }

}
