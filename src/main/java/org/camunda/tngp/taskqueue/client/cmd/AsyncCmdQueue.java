package org.camunda.tngp.taskqueue.client.cmd;

import org.camunda.tngp.transport.util.BoundedArrayQueue;

public class AsyncCmdQueue<T> extends BoundedArrayQueue<AsyncResult<T>>
{
    public AsyncCmdQueue(int capacity)
    {
        super(capacity);
    }

    public boolean hasCapacity()
    {
        return getCapacity() - size() > 0;
    }

    public T awaitNext()
    {
        final AsyncResult<T> request = poll();

        if(request != null)
        {
            request.await();
        }

        return request.get();
    }

    public T pollNext()
    {
        final AsyncResult<T> request = peek();

        if(request != null)
        {
            if(request.poll())
            {
                remove();
                return request.get();
            }
        }

        return null;
    }

    public void awaitAll()
    {
        for (AsyncResult<T> request : this)
        {
            try
            {
                request.await();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public void closeAll()
    {
        for (AsyncResult<T> request : this)
        {
            try
            {
                request.await();
                request.get();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        clear();
    }

}
