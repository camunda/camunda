package io.zeebe.dispatcher.impl;

import java.util.function.Consumer;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.util.actor.Actor;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

/**
 * The conductor performs maintenance operations on the dispatcher
 * Duties:
 *
 * <ul>
 * <li>Clean log buffer on rollover</li>
 * <li>Advance publisher limit</li>
 * </ul>
 */
public class DispatcherConductor implements Actor, Consumer<DispatcherConductorCommand>
{
    public static final String NAME_TEMPLATE = "%s.dispatcher-conductor";

    protected final ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> cmdQueue;

    protected Dispatcher dispatcher;

    protected String name;

    public DispatcherConductor(String dispatcherName, DispatcherContext dispatcherContext, Dispatcher dispatcher)
    {
        this.dispatcher = dispatcher;
        this.cmdQueue = dispatcherContext.getDispatcherCommandQueue();
        this.name = String.format(NAME_TEMPLATE, dispatcherName);
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public int getPriority(long now)
    {
        return PRIORITY_LOW;
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = cmdQueue.drain(this);

        if (!dispatcher.isClosed())
        {
            workCount += dispatcher.updatePublisherLimit();
            workCount += dispatcher.getLogBuffer().cleanPartitions();
        }

        return workCount;
    }

    @Override
    public void accept(DispatcherConductorCommand cmd)
    {
        cmd.execute(this);
    }

}
