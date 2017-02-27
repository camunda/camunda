package org.camunda.tngp.broker.it.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public class ParallelRequests
{
    protected List<FutureTask<?>> tasks = new ArrayList<>();

    public <T> SilentFuture<T> submitRequest(Callable<T> request)
    {
        final FutureTask<T> futureTask = new FutureTask<>(request);
        tasks.add(futureTask);
        return new SilentFuture<>(futureTask);
    }

    public void execute()
    {
        final List<Thread> threads = new ArrayList<>();

        for (FutureTask<?> task : tasks)
        {
            threads.add(new Thread(task));
        }

        for (Thread thread : threads)
        {
            thread.start();
        }

        for (Thread thread : threads)
        {
            try
            {
                thread.join();
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }

    }

    public static ParallelRequests prepare()
    {
        return new ParallelRequests();
    }

    public static class SilentFuture<T>
    {
        Future<T> future;

        public SilentFuture(Future<T> future)
        {
            this.future = future;
        }

        public T get()
        {
            try
            {
                return future.get();
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
            catch (ExecutionException e)
            {
                // ignore
                e.printStackTrace();
                return null;
            }
        }
    }



}
