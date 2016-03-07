package net.long_running.transport.impl.agent;

import java.util.function.Consumer;

import net.long_running.transport.impl.BaseChannelImpl;
import net.long_running.transport.impl.ClientChannelImpl;
import net.long_running.transport.impl.TransportContext;
import net.long_running.transport.impl.media.ReadTransportPoller;
import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

/**
 *
 * Responsibilities
 * <ul>
 * <li>Polling the media for READ and performing {@link ClientChannelImpl#receive()} operations</li>
 * <li>Closing channels</li>
 */
public class Receiver implements Agent, Consumer<ReceiverCmd>
{
    protected final ManyToOneConcurrentArrayQueue<ReceiverCmd> cmdQueue;

    protected final ReadTransportPoller transportPoller;

    protected final ManyToOneConcurrentArrayQueue<SenderCmd> toSenderCmdQueue;

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

    public void registerChannel(BaseChannelImpl c)
    {
        transportPoller.addChannel(c);
    }

    public void removeChannel(BaseChannelImpl c)
    {
        transportPoller.removeChannel(c);
    }

    public ManyToOneConcurrentArrayQueue<ReceiverCmd> getCmdQueue()
    {
        return this.cmdQueue;
    }

    public void onChannelClose(final BaseChannelImpl channel)
    {
        transportPoller.removeChannel(channel);
        channel.closeConnection();
        toSenderCmdQueue.add((s) ->
        {
           s.removeChannel(channel);
        });
    }

}
