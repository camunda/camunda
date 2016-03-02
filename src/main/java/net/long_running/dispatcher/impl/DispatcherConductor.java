package net.long_running.dispatcher.impl;

import static net.long_running.dispatcher.Dispatcher.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.long_running.dispatcher.AsyncCompletionCallback;
import net.long_running.dispatcher.Dispatcher;
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

    public static final String NAME = "net.long_running.dispatcher.conductor";

    protected final ManyToOneConcurrentArrayQueue<DispatcherConductorCommand> cmdQueue;

    protected final List<Dispatcher> dispatchers = new ArrayList<>(10);

    protected final Map<Dispatcher, AsyncCompletionCallback<Dispatcher>> closeCallbacks = new HashMap<>(10);
    protected final Map<Dispatcher, AsyncCompletionCallback<Dispatcher>> startCallbacks = new HashMap<>(10);

    protected final boolean isShared;
    protected final DispatcherContext context;

    public DispatcherConductor(DispatcherContext dispatcherContext, boolean isShared)
    {
        this.cmdQueue = dispatcherContext.getDispatcherCommandQueue();
        this.context = dispatcherContext;
        this.isShared = isShared;
    }

    public String roleName()
    {
        return NAME;
    }

    public int doWork() throws Exception
    {
        int workCount = cmdQueue.drain(this);

        for (Dispatcher dispatcher : dispatchers)
        {
            final int dispatcherStatus = dispatcher.getStatus();

            switch (dispatcherStatus)
            {
            case STATUS_CLOSE_REQUESTED:
                workCount += trackDispatcherClose(dispatcher);
                break;

            case STATUS_NEW:
                workCount += acivateDispatcher(dispatcher);
                break;

            case STATUS_ACTIVE:
                workCount += dispatcher.updatePublisherLimit();
                workCount += dispatcher.getLogBuffer().cleanPartitions();
                break;
            }
        }

        return workCount;
    }

    protected int trackDispatcherClose(final Dispatcher dispatcher)
    {
        dispatcher.setPublisherLimitOrdered(-1);
        if(dispatcher.isReadyToClose())
        {
            cmdQueue.add((cct) ->
            {
                dispatcher.doClose();

                if(!isShared)
                {
                    context.close();
                }
                else {
                    notifyClose(dispatcher);
                }


            });
        }

        return 1;
    }

    protected void notifyClose(final Dispatcher dispatcher)
    {
        dispatchers.remove(dispatcher);

        final AsyncCompletionCallback<Dispatcher> closeCallback = closeCallbacks.remove(dispatcher);
        if(closeCallback != null)
        {
            closeCallback.onComplete(null, dispatcher);
        }
    }

    protected void notifyActivate(Dispatcher dispatcher)
    {
        final AsyncCompletionCallback<Dispatcher> startCallback = startCallbacks.remove(dispatcher);
        if(startCallback != null)
        {
            startCallback.onComplete(null, dispatcher);
        }
    }

    @Override
    public void onClose()
    {
        if(!isShared)
        {
            notifyClose(dispatchers.get(0));
        }
    }

    protected int acivateDispatcher(Dispatcher dispatcher)
    {
        dispatcher.setStateOrdered(STATUS_ACTIVE);
        dispatcher.updatePublisherLimit();
        notifyActivate(dispatcher);
        return 2;
    }

    @Override
    public void accept(DispatcherConductorCommand cmd)
    {
        cmd.execute(this);
    }

    public void requestStartDispatcher(Dispatcher dispatcher, AsyncCompletionCallback<Dispatcher> startCallback)
    {
        final int status = dispatcher.getStatus();
        if(status == STATUS_NEW)
        {
            this.dispatchers.add(dispatcher);
            this.startCallbacks.put(dispatcher, startCallback);
        }
        else
        {
            if(startCallback != null)
            {
                startCallback.onComplete(new IllegalStateException("Cannot start this dispatcher, is not in state new"), dispatcher);
            }
        }
    }

    public void requestCloseDispatcher(Dispatcher dispatcher, AsyncCompletionCallback<Dispatcher> closeCallback)
    {
        final int status = dispatcher.getStatus();

        if(status == STATUS_ACTIVE)
        {
            closeCallbacks.put(dispatcher, closeCallback);
            dispatcher.setStateOrdered(STATUS_CLOSE_REQUESTED);
        }
        else
        {
            if(closeCallback != null)
            {
                closeCallback.onComplete(new IllegalStateException("Cannot close dispatcher, dispatcher is in state "+status), dispatcher);
            }
        }
    }

}
