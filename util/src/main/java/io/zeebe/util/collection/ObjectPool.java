package io.zeebe.util.collection;

import java.util.function.Function;

import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;

public class ObjectPool<T>
{
    protected ManyToManyConcurrentArrayQueue<T> queue;

    public ObjectPool(int capacity, Function<ObjectPool<T>, T> objectFactory)
    {
        this.queue = new ManyToManyConcurrentArrayQueue<>(capacity);

        for (int i = 0; i < capacity; i++)
        {
            this.queue.add(objectFactory.apply(this));
        }
    }

    public void returnObject(T pooledFuture)
    {
        queue.add(pooledFuture);
    }

    public T request()
    {
        return this.queue.poll();
    }
}
