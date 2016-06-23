package org.camunda.tngp.transport.impl.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.camunda.tngp.transport.impl.TransportChannelImpl;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.impl.media.ReadTransportPoller;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionImpl;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

/**
 *
 * Responsibilities
 * <ul>
 * <li>Polling the media for READ and performing {@link ClientChannelImpl#receive()} operations</li>
 * <li>Processing Request timeouts</li>
 * <li>Closing connections</li>
 * <li>Closing channels</li>
 * </ul>
 */
public class Receiver implements Agent, Consumer<ReceiverCmd>
{
    protected final ManyToOneConcurrentArrayQueue<ReceiverCmd> cmdQueue;

    protected final ReadTransportPoller transportPoller;

    protected final ManyToOneConcurrentArrayQueue<SenderCmd> toSenderCmdQueue;

    protected final List<TransportConnectionImpl> connectionsToClose = new ArrayList<>(10);

    public Receiver(TransportContext context)
    {
        cmdQueue = context.getReceiverCmdQueue();
        toSenderCmdQueue = context.getSenderCmdQueue();
        transportPoller = new ReadTransportPoller(this);
    }

    public int doWork() throws Exception
    {
        int work = 0;

        work += cmdQueue.drain(this);
        work += transportPoller.pollNow();

        return work;
    }

    public String roleName()
    {
        return "receiver";
    }

    @Override
    public void accept(ReceiverCmd t)
    {
        t.execute(this);
    }

    public void registerChannel(TransportChannelImpl c, CompletableFuture<Void> receiverFuture)
    {
        transportPoller.addChannel(c);
        receiverFuture.complete(null);
    }

    public void removeChannel(TransportChannelImpl c)
    {
        transportPoller.removeChannel(c);
    }

    public ManyToOneConcurrentArrayQueue<ReceiverCmd> getCmdQueue()
    {
        return this.cmdQueue;
    }

}
