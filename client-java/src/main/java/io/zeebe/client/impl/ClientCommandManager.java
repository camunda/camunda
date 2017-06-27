package io.zeebe.client.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.impl.cmd.AbstractCmdImpl;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.actor.Actor;


public class ClientCommandManager implements Actor
{
    protected final ManyToOneConcurrentArrayQueue<Runnable> commandQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected final Consumer<Runnable> commandConsumer = Runnable::run;

    protected final List<ClientCommandController<?>> commandControllers = new ArrayList<>();

    protected final ClientTransport transport;
    protected final ClientTopologyManager topologyManager;

    public ClientCommandManager(final ClientTransport transport, final ClientTopologyManager topologyManager)
    {
        this.transport = transport;
        this.topologyManager = topologyManager;
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = commandQueue.drain(commandConsumer);

        final Iterator<ClientCommandController<?>> iterator = commandControllers.iterator();
        while (iterator.hasNext())
        {
            final ClientCommandController controller = iterator.next();

            if (!controller.isClosed())
            {
                workCount = controller.doWork();
            }
            else
            {
                iterator.remove();
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
            final ClientCommandController<R> controller = new ClientCommandController<>(transport, topologyManager, command, future);
            commandControllers.add(controller);
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
