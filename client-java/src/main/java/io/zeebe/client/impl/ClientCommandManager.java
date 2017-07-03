package io.zeebe.client.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.impl.cmd.AbstractCmdImpl;
import io.zeebe.transport.ChannelManager;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;
import io.zeebe.util.actor.Actor;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;


public class ClientCommandManager implements Actor
{
    protected final ManyToOneConcurrentArrayQueue<Runnable> commandQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected final Consumer<Runnable> commandConsumer = Runnable::run;

    protected final ClientCommandController[] commandControllers = new ClientCommandController[128];

    protected final ChannelManager channelManager;
    protected final TransportConnectionPool connectionPool;
    protected final ClientTopologyManager topologyManager;

    public ClientCommandManager(final ChannelManager channelManager, final TransportConnectionPool connectionPool, final ClientTopologyManager topologyManager)
    {
        this.channelManager = channelManager;
        this.connectionPool = connectionPool;
        this.topologyManager = topologyManager;
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = commandQueue.drain(commandConsumer);

        for (int i = 0; i < commandControllers.length; i++)
        {
            final ClientCommandController controller = commandControllers[i];

            if (controller != null)
            {
                if (!controller.isClosed())
                {
                    workCount = controller.doWork();
                }
                else
                {
                    commandControllers[i] = null;
                }
            }
        }

        return workCount;
    }

    public <R> CompletableFuture<R> executeAsync(final AbstractCmdImpl<R> command)
    {
        command.getRequestWriter().validate();

        final CompletableFuture<R> future = new CompletableFuture<>();

        commandQueue.add(() ->
        {
            final ClientCommandController<R> controller = new ClientCommandController<>(channelManager, connectionPool, topologyManager, command, future);

            for (int i = 0; i < commandControllers.length; i++)
            {
                if (commandControllers[i] == null)
                {
                    commandControllers[i] = controller;
                    return;
                }
            }

            future.completeExceptionally(new RuntimeException("Max num of commands reached"));
        });

        return future;
    }

    public <R> R execute(final AbstractCmdImpl<R> command)
    {
        try
        {
            return executeAsync(command).get();
        }
        catch (final InterruptedException e)
        {
            throw new RuntimeException("Interrupted while executing command");
        }
        catch (final ExecutionException e)
        {
            throw (RuntimeException) e.getCause();
        }
    }
}
