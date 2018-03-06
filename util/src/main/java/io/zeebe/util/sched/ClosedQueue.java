package io.zeebe.util.sched;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

public class ClosedQueue implements Queue<ActorJob>
{

    @Override
    public int size()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(Object o)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<ActorJob> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends ActorJob> c)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(ActorJob e)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean offer(ActorJob e)
    {
        e.failFuture("Actor is closed");

        return true;
    }

    @Override
    public ActorJob remove()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActorJob poll()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActorJob element()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActorJob peek()
    {
        throw new UnsupportedOperationException();
    }

}
