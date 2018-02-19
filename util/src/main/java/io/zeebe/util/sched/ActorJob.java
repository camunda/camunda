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

import java.util.concurrent.Callable;

import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
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
                resultFuture = null;
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
            if (isTriggeredBySubscription()
                    || (isAutoCompleting && runnable == null)
                    || isDoneCalled)
            {
                state = ActorState.TERMINATED;
            }
            else
            {
                state = ActorState.QUEUED;
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

        if (ActorRunnerMetrics.SHOULD_RECORD_JOB_EXECUTION_TIME)
        {
            final ActorRunnerMetrics metrics = runner.getMetrics();
            metrics.recordJobExecutionTime(System.nanoTime() - before);
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
        if (this.runnable == null && this.callable == null && !isTriggeredBySubscription())
        {
            this.runnable = runnable;
            return true;
        }
        return false;

    }

    public ActorFuture setCallable(Callable<?> callable)
    {
        this.callable = callable;
        this.resultFuture = new CompletableActorFuture<>();
        return resultFuture;
    }

    /**
     * used to recycle the job object
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

}
