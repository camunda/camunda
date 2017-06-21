package org.camunda.tngp.transport;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.impl.agent.Conductor;

public class Transport implements AutoCloseable
{
    public static final int STATE_OPEN = 0;
    public static final int STATE_CLOSING = 1;
    public static final int STATE_CLOSED = 2;

    protected static final AtomicIntegerFieldUpdater<Transport> STATE_FIELD = AtomicIntegerFieldUpdater.newUpdater(Transport.class, "state");

    protected final TransportContext transportContext;
    protected final Dispatcher sendBuffer;
    protected final Conductor conductor;

    protected volatile int state;

    public Transport(Conductor conductor, TransportContext transportContext)
    {
        this.conductor = conductor;
        this.transportContext = transportContext;
        this.sendBuffer = transportContext.getSendBuffer();

        STATE_FIELD.set(this, STATE_OPEN);
    }

    public ChannelManagerBuilder createClientChannelPool()
    {
        if (STATE_OPEN != STATE_FIELD.get(this))
        {
            throw new IllegalStateException("Cannot create client channel on " + this + ", transport is not open.");
        }

        return new ChannelManagerBuilder(conductor);
    }

    public CompletableFuture<Void> registerChannelListener(TransportChannelListener listener)
    {
        return conductor.registerChannelListenerAsync(listener);
    }

    public CompletableFuture<Void> removeChannelListener(TransportChannelListener listener)
    {
        return conductor.removeChannelListenerAsync(listener);
    }

    public ServerSocketBindingBuilder createServerSocketBinding(SocketAddress addr)
    {
        if (STATE_OPEN != STATE_FIELD.get(this))
        {
            throw new IllegalStateException("Cannot create server socket on " + this + ", transport is not open.");
        }

        return new ServerSocketBindingBuilder(conductor, addr.toInetSocketAddress());
    }

    public Dispatcher getSendBuffer()
    {
        return sendBuffer;
    }

    public CompletableFuture<Transport> closeAsync()
    {
        if (STATE_FIELD.compareAndSet(this, STATE_OPEN, STATE_CLOSING))
        {
            return conductor.closeAsync().thenApply((v) ->
            {
                if (!transportContext.isSendBufferExternallyManaged())
                {
                    transportContext.getSendBuffer().closeAsync();
                }

                transportContext.getReceiver().close();
                transportContext.getSender().close();
                transportContext.getConductor().close();

                return this;
            });
        }
        else
        {
            return CompletableFuture.completedFuture(this);
        }
    }

    @Override
    public void close()
    {
        closeAsync().join();
    }

}
