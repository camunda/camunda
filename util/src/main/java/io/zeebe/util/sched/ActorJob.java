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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import io.zeebe.util.sched.future.*;
import io.zeebe.util.sched.metrics.ActorRunnerMetrics;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class ActorJob
{
    ActorState state;

    ZbActor actor;
    ActorTask task;

    ActorJob next;

    private Callable<?> callable;
    private Runnable runnable;
    private Object invocationResult;
    private boolean isAutoCompleting;
    private boolean isDoneCalled;

    private ActorFuture resultFuture;

    ActorTaskRunner runner;

    private ActorSubscription subscription;

    private final List<ActorConditionImpl> triggeredConditions = new ArrayList<>();

    public void onJobAddedToTask(ActorTask task)
    {
        this.actor = task.actor;
        this.task = task;
        this.state = ActorState.QUEUED;
    }

    void execute(ActorTaskRunner runner)
    {
        runner.getMetrics().incrementJobCount();

        this.runner = runner;
        try
        {
            invoke(runner);

            if (resultFuture != null)
            {
                resultFuture.complete(invocationResult);
            }

        }
        catch (Exception e)
        {
            e.printStackTrace();

            if (resultFuture != null)
            {
                resultFuture.completeExceptionally(e);
            }
            // TODO: what else to do?
        }
        finally
        {
            this.runner = null;

            // in any case, success or exception, decide if the job should be resubmitted
            if (state != ActorState.BLOCKED)
            {
                if (isAutoCompleting || isDoneCalled || isTriggeredBySubscription())
                {
                    state = ActorState.TERMINATED;
                }
                else
                {
                    state = ActorState.QUEUED;
                }
            }
        }
    }

    private void invoke(ActorTaskRunner runner) throws Exception
    {
        long before = -1;
        if (ActorRunnerMetrics.SHOULD_RECORD_JOB_EXECUTION_TIME)
        {
            before = System.nanoTime();
        }
        if (callable != null)
        {
            invocationResult = callable.call();
        }
        else
        {
            if (!isTriggeredBySubscription())
            {
                // TODO: preempt after fixed number of iterations
                while (runnable != null && !task.shouldYield && !isDoneCalled)
                {
                    final Runnable r = this.runnable;

                    if (isAutoCompleting)
                    {
                        this.runnable = null;
                    }

                    r.run();
                }
            }
            else
            {
                runnable.run();
            }
        }

        processTriggeredConditions();

        if (ActorRunnerMetrics.SHOULD_RECORD_JOB_EXECUTION_TIME)
        {
            final ActorRunnerMetrics metrics = runner.getMetrics();
            metrics.recordJobExecutionTime(System.nanoTime() - before);
        }
    }

    private void processTriggeredConditions()
    {
        final int conditioncount = triggeredConditions.size();
        for (int i = conditioncount - 1; i >= 0; i--)
        {
            final ActorConditionImpl condition = triggeredConditions.remove(i);
            condition.trigger();
        }
    }

    /**
     * Append a child task to this task. The new child task is appended to the list of tasks
     * spawned by this task such that it is executed last.
     */
    protected void appendChild(ActorJob spawnedTask)
    {
        spawnedTask.next = this.next;
        this.next = spawnedTask;
    }

    public void append(ActorJob newJob)
    {
        ActorJob job = this;

        while (job.next != null)
        {
            job = job.next;
        }

        job.appendChild(newJob);
    }

    /**
     * remove this task from the chain of tasks to execute
     * @return the next task
     */
    protected ActorJob getNext()
    {
        final ActorJob next = this.next;
        this.next = null;
        return next;
    }

    public boolean setRunnable(Runnable runnable)
    {
        if (this.runnable == null)
        {
            this.runnable = runnable;
            return true;
        }
        return false;

    }

    public Future setCallable(Callable<?> callable)
    {
        this.callable = callable;
        this.resultFuture = new CompletableActorFuture<>();
        return resultFuture;
    }

    public <T> void setBlockOnFuture(ActorFuture<T> future, BiConsumer<T, Throwable> callback)
    {
        runnable = new AwaitFutureRunnable<>(this, future, callback);
    }

    static class AwaitFutureRunnable<T> implements Runnable
    {
        ActorFuture<T> future;
        BiConsumer<T, Throwable> callback;
        ActorJob job;
        ActorTask task;

        AwaitFutureRunnable(ActorJob job, ActorFuture<T> future, BiConsumer<T, Throwable> callback)
        {
            this.job = job;
            this.task = job.task;
            this.future = future;
            this.callback = callback;
        }

        @Override
        public void run()
        {
            task.awaitFuture = future;
            job.state = ActorState.BLOCKED;

            if (!future.block(createContinuationJob(future, callback)))
            {
                task.submittedJobs.offer(createContinuationJob(future, callback));
            }
        }

        private <T> ActorJob createContinuationJob(ActorFuture<T> future, BiConsumer<T, Throwable> callback)
        {
            final ActorJob continuationJob = new ActorJob();
            continuationJob.setAutoCompleting(true);
            continuationJob.onJobAddedToTask(task);
            continuationJob.setRunnable(new FutureContinuationRunnable<>(task, future, callback));
            return continuationJob;
        }

    }

    static class FutureContinuationRunnable<T> implements Runnable
    {
        ActorFuture<T> future;
        BiConsumer<T, Throwable> callback;
        ActorTask task;

        FutureContinuationRunnable(ActorTask task, ActorFuture<T> future, BiConsumer<T, Throwable> callback)
        {
            this.task = task;
            this.future = future;
            this.callback = callback;
        }

        @Override
        public void run()
        {
            try
            {
                if (task.awaitFuture == future)
                {
                    task.awaitFuture = null;
                    final T res = future.get();
                    callback.accept(res, null);
                }
            }
            catch (ExecutionException e)
            {
                callback.accept(null, e);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * used to recycle the task object
     */
    void reset()
    {
        state = ActorState.NOT_SCHEDULED;

        next = null;
        actor = null;

        task = null;
        runner = null;

        callable = null;
        runnable = null;
        invocationResult = null;
        isAutoCompleting = true;
        isDoneCalled = false;

        resultFuture = null;
        subscription = null;
    }

    public void markDone()
    {
        if (isAutoCompleting)
        {
            throw new UnsupportedOperationException("Incorrect use of actor.done(). Can only be called in methods submitted using actor.runUntilDone(Runnable r)");
        }

        isDoneCalled = true;
    }

    public void setAutoCompleting(boolean isAutoCompleting)
    {
        this.isAutoCompleting = isAutoCompleting;
    }

    public void onFutureCompleted()
    {
        task.onFutureCompleted(this);
    }

    @Override
    public String toString()
    {
        String toString = "";

        if (runnable != null)
        {
            toString += runnable.getClass().getName();
        }
        if (callable != null)
        {
            toString += callable.getClass().getName();
        }

        toString += " " + state;

        return toString;
    }

    public boolean isContinuationSignal(ActorFuture awaitFuture)
    {
        if (runnable != null && runnable instanceof FutureContinuationRunnable)
        {
            return ((FutureContinuationRunnable) runnable).future == awaitFuture;
        }

        return false;
    }

    public boolean isTriggeredBySubscription()
    {
        return subscription != null;
    }

    public void setSubscription(ActorSubscription subscription)
    {
        this.subscription = subscription;
        task.addSubscription(subscription);
    }

    public ActorSubscription getSubscription()
    {
        return subscription;
    }

    public ActorTask getTask()
    {
        return task;
    }

    public void addTriggeredCondition(ActorConditionImpl condition)
    {
        triggeredConditions.add(condition);
    }
}
