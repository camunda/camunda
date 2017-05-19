package org.camunda.tngp.client.task.impl.subscription;

import java.util.function.Consumer;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class DeferredCommandQueue<T> implements CommandQueue<T>
{

    protected final ManyToOneConcurrentArrayQueue<T> cmdQueue;
    protected final Consumer<T> consumer;

    public DeferredCommandQueue(Consumer<T> consumer)
    {
        this.consumer = consumer;
        this.cmdQueue = new ManyToOneConcurrentArrayQueue<>(32);
    }


    @Override
    public void add(T cmd)
    {
        cmdQueue.add(cmd);
    }

    @Override
    public int drain()
    {
        return cmdQueue.drain(consumer);
    }
}
