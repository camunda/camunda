package org.camunda.tngp.dispatcher.impl;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.dispatcher.Dispatcher;

/**
 * The conductor performs maintenance operations on the dispatcher
 * Duties:
 *
 * <ul>
 * <li>Clean log buffer on rollover</li>
 * <li>Advance publisher limit</li>
 * </ul>
 */
public class DispatcherConductor implements Agent, Consumer<DispatcherConductorCommand>
{
    public static final String NAME_TEMPLATE = "%s.dispatcher-conductor";

    protected final ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> cmdQueue;

    protected Dispatcher dispatcher;
    protected CompletableFuture<Dispatcher> closeFuture;
    protected CompletableFuture<Dispatcher> startFuture;

    protected final DispatcherContext context;
    protected String name;

    public DispatcherConductor(String dispatcherName, DispatcherContext dispatcherContext, Dispatcher dispatcher)
    {
        this.dispatcher = dispatcher;
        this.cmdQueue = dispatcherContext.getDispatcherCommandQueue();
        this.context = dispatcherContext;
        this.name = String.format(NAME_TEMPLATE, dispatcherName);
    }

    public String roleName()
    {
        return name;
    }

    public int doWork() throws Exception
    {
        int workCount = cmdQueue.drain(this);

        workCount += dispatcher.updatePublisherLimit();
        workCount += dispatcher.getLogBuffer().cleanPartitions();

        return workCount;
    }

    @Override
    public void accept(DispatcherConductorCommand cmd)
    {
        cmd.execute(this);
    }

    public Object exit()
    {
        return null;
    }

}
