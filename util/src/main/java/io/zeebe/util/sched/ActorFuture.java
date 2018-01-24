/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util.sched;

import java.util.concurrent.*;

import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

public class ActorFuture<V> implements Future<V>
{
    private final ManyToOneConcurrentLinkedQueue<ActorJob> blockedTasks = new ManyToOneConcurrentLinkedQueue<>();

    private V invocationResult;

    private Exception exception;

    private volatile boolean isDone = false;

    public void markDone(V r, Exception e)
    {
        this.invocationResult = r;
        this.exception = e;
        this.isDone = true;

        // if you feel need to simplify, read javadoc in
        // ManyToOneConcurrentLinkedQueue
        while (!blockedTasks.isEmpty())
        {
            final ActorJob blocked = blockedTasks.poll();
            if (blocked != null)
            {
                try
                {
                    blocked.onFutureCompleted();
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    }

    public boolean block(ActorJob job)
    {
        blockedTasks.add(job);
        return !isDone;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        throw new UnsupportedOperationException("cancel() is not implemented by this future.");
    }

    @Override
    public boolean isCancelled()
    {
        throw new UnsupportedOperationException("cancel() is not implemented by this future.");
    }

    @Override
    public boolean isDone()
    {
        return isDone;
    }

    @Override
    public V get() throws InterruptedException, ExecutionException
    {
        try
        {
            return get(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
        if (!isDone())
        {
            if (ActorTaskRunner.current() != null)
            {
                throw new IllegalStateException(
                        "Actor call get() on future which has not completed. " + "Actors must be non-blocking. Use actor.awaitFuture().");
            }
            else
            {
                final long waitTime = System.currentTimeMillis() + unit.toMillis(timeout) + 1;
                // TODO: better implementation of blocking for non-actor threads
                while (!isDone)
                {
                    if (System.currentTimeMillis() > waitTime)
                    {
                        throw new TimeoutException();
                    }

                    Thread.yield();
                }
            }
        }

        if (exception != null)
        {
            throw new ExecutionException(exception);
        }

        return invocationResult;
    }

    class ActorTaskWithLockCount
    {
        ActorTask task;
        long lockCount;

        ActorTaskWithLockCount(ActorTask task, long lockCount)
        {
            this.task = task;
            this.lockCount = lockCount;
        }
    }

}
