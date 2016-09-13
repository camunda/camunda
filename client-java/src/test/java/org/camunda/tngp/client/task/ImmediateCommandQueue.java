package org.camunda.tngp.client.task;

import java.util.function.Consumer;

import org.camunda.tngp.client.task.impl.CommandQueue;

public class ImmediateCommandQueue<T> implements CommandQueue<T>
{
    protected Consumer<T> consumer;

    public ImmediateCommandQueue(Consumer<T> consumer)
    {
        this.consumer = consumer;
    }

    @Override
    public void add(T cmd)
    {
        consumer.accept(cmd);
    }

    @Override
    public int drain()
    {
        // does nothing
        return 0;
    }

}
