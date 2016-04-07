package org.camunda.tngp.dispatcher.impl;

import static org.camunda.tngp.dispatcher.Dispatcher.STATUS_ACTIVE;
import static org.camunda.tngp.dispatcher.Dispatcher.STATUS_CLOSE_REQUESTED;
import static org.camunda.tngp.dispatcher.Dispatcher.STATUS_NEW;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.camunda.tngp.dispatcher.Dispatcher;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

/**
 * The conductor performs maintenance operations on the dispatcher
 * Duties:
 *
 * <ul>
 * <li>Clean log buffer on rollover</li>
 * <li>Advance publisher limit</li>
 * <li>Performing dispatcher lifecycle operations (start, close)</li>
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

    public DispatcherConductor(String dispatcherName, DispatcherContext dispatcherContext)
    {
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

        if(dispatcher != null)
        {
            final int dispatcherStatus = dispatcher.getStatus();
            switch (dispatcherStatus)
            {
                case STATUS_CLOSE_REQUESTED:
                    workCount += trackDispatcherClose();
                    break;

                case STATUS_NEW:
                    workCount += acivateDispatcher();
                    break;

                case STATUS_ACTIVE:
                    workCount += dispatcher.updatePublisherLimit();
                    workCount += dispatcher.getLogBuffer().cleanPartitions();
                    break;
            }
        }

        return workCount;
    }

    protected int trackDispatcherClose()
    {
        dispatcher.setPublisherLimitOrdered(-1);
        if(dispatcher.isReadyToClose())
        {
            cmdQueue.add((cct) ->
            {
                dispatcher.doClose();
                notifyClose();
            });
        }
        return 1;
    }

    protected void notifyClose()
    {
        closeFuture.thenRunAsync(() -> context.close());
        closeFuture.complete(dispatcher);
    }

    protected void notifyActivate()
    {
        startFuture.complete(dispatcher);
    }

    protected int acivateDispatcher()
    {
        dispatcher.setStateOrdered(STATUS_ACTIVE);
        dispatcher.updatePublisherLimit();
        notifyActivate();
        return 1;
    }

    @Override
    public void accept(DispatcherConductorCommand cmd)
    {
        cmd.execute(this);
    }

    public void requestStartDispatcher(Dispatcher dispatcher, CompletableFuture<Dispatcher> startFuture)
    {
        final int status = dispatcher.getStatus();
        if(status == STATUS_NEW)
        {
            this.dispatcher = dispatcher;
            this.startFuture = startFuture;
        }
        else
        {
            if(startFuture != null)
            {
                startFuture.completeExceptionally(new IllegalStateException("Cannot start this dispatcher, is not in state new"));
            }
        }
    }

    public void requestCloseDispatcher(Dispatcher dispatcher, CompletableFuture<Dispatcher> closeFuture)
    {
        final int status = dispatcher.getStatus();

        if(status == STATUS_ACTIVE)
        {
            this.closeFuture = closeFuture;
            dispatcher.setStateOrdered(STATUS_CLOSE_REQUESTED);
        }
        else
        {
            if(closeFuture != null)
            {
                closeFuture.completeExceptionally(new IllegalStateException("Cannot close dispatcher, dispatcher is in state "+status));
            }
        }
    }

}
