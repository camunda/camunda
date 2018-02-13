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

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.zeebe.util.sched.channel.ChannelConsumerCondition;
import io.zeebe.util.sched.channel.ConsumableChannel;
import io.zeebe.util.sched.future.ActorFuture;

public class ActorControl
{
    private final ZbActor actor;

    final ActorTask task;

    public ActorControl(ZbActor actor)
    {
        this.actor = actor;
        this.task = new ActorTask(actor);
    }

    public void consume(ConsumableChannel channel, Runnable consumer)
    {
        ensureCalledFromWithinActor("consume(...)");

        final ActorJob job = new ActorJob();
        job.setRunnable(consumer);
        job.setAutoCompleting(false);
        job.onJobAddedToTask(task);

        final ChannelConsumerCondition subscription = new ChannelConsumerCondition(job, channel);
        job.setSubscription(subscription);

        channel.registerConsumer(subscription);
    }

    public void pollBlocking(Runnable condition, Runnable action)
    {
        ensureCalledFromWithinActor("pollBlocking(...)");

        final ActorJob job = new ActorJob();
        job.setRunnable(action);
        job.onJobAddedToTask(task);

        final BlockingPollSubscription subscription = new BlockingPollSubscription(job, condition, task.getScheduler(), true);
        job.setSubscription(subscription);

        subscription.submit();
    }

    public ActorCondition onCondition(String conditionName, Runnable conditionAction)
    {
        ensureCalledFromWithinActor("onCondition(...)");

        final ActorJob job = new ActorJob();
        job.setRunnable(conditionAction);
        job.onJobAddedToTask(task);

        final ActorConditionImpl condition = new ActorConditionImpl(conditionName, job);
        job.setSubscription(condition);

        return condition;
    }

    @SuppressWarnings("unchecked")
    public <T> ActorFuture<T> call(Callable<T> callable)
    {
        final ActorTaskRunner runner = ActorTaskRunner.current();
        if (runner != null && runner.getCurrentTask() == task)
        {
            throw new UnsupportedOperationException("Incorrect usage of actor.call(...) cannot be called from current actor.");
        }

        final ActorJob job = new ActorJob();
        final ActorFuture<T> future = job.setCallable(callable);
        job.onJobAddedToTask(task);
        job.setAutoCompleting(true);
        task.submit(job);

        return future;
    }

    public ActorFuture<Void> call(Runnable r)
    {
        final Callable<Void> c = () ->
        {
            r.run();
            return null;
        };

        return call(c);
    }

    public void run(Runnable runnable)
    {
        scheduleRunnable(runnable, true);
    }

    /** run a blocking task */
    public void runBlocking(Runnable runnable)
    {
        ensureCalledFromWithinActor("pollBlocking(...)");

        final ActorJob noop = new ActorJob();
        noop.onJobAddedToTask(task);
        noop.setAutoCompleting(true);
        noop.setRunnable(() ->
        {
            // noop
        });

        final BlockingPollSubscription subscription = new BlockingPollSubscription(noop, runnable, task.getScheduler(), false);
        noop.setSubscription(subscription);

        subscription.submit();
    }

    public void runBlocking(Runnable runnable, Consumer<Throwable> whenDone)
    {
        final RunnableAdapter<Void> adapter = RunnableAdapter.wrapRunnable(runnable);

        ensureCalledFromWithinActor("pollBlocking(...)");

        final ActorJob noop = new ActorJob();
        noop.onJobAddedToTask(task);
        noop.setAutoCompleting(true);
        noop.setRunnable(adapter.wrapConsumer(whenDone));

        final BlockingPollSubscription subscription = new BlockingPollSubscription(noop, adapter, task.getScheduler(), false);
        noop.setSubscription(subscription);

        subscription.submit();
    }

    /**
     * Run the provided runnable repeatedly until it calls {@link #done()}.
     * To be used for jobs which may experience backpressure.
     */
    public void runUntilDone(Runnable runnable)
    {
        scheduleRunnable(runnable, false);
    }

    public void runDelayed(Duration delay, Runnable runnable)
    {
        ensureCalledFromWithinActor("runDelayed(...)");
        scheduleTimer(delay, false, runnable);
    }

    /**
     * Like {@link #run(Runnable)} but submits the runnable to the end end of the actor's queue such that
     * other other actions may be executed before this. This method is useful in case
     * an actor is in a (potentially endless) loop and it should be able to interrupt it.
     *
     * @param action the action to run.
     */
    public void submit(Runnable action)
    {
        final ActorTaskRunner currentActorRunner = ensureCalledFromActorRunner("run(...)");

        final ActorJob job = currentActorRunner.newJob();
        job.setRunnable(action);
        job.setAutoCompleting(true);
        job.onJobAddedToTask(task);
        task.submit(job);
        yield();
    }

    public void runAtFixedRate(Duration delay, Runnable runnable)
    {
        ensureCalledFromWithinActor("runAtFixedRate(...)");
        scheduleTimer(delay, true, runnable);
    }

    private void scheduleTimer(Duration delay, boolean isRecurring, Runnable runnable)
    {
        final ActorJob job = new ActorJob();
        job.setRunnable(runnable);
        job.onJobAddedToTask(task);

        final TimerSubscription timerSubscription = new TimerSubscription(job, delay.toNanos(), TimeUnit.NANOSECONDS, isRecurring);
        job.setSubscription(timerSubscription);

        timerSubscription.submit();
    }

    public <T> void await(ActorFuture<T> f, BiConsumer<T, Throwable> callback)
    {
        final ActorJob currentJob = ensureCalledFromWithinActor("await(...)");

        final ActorJob blockedJob = new ActorJob();
        blockedJob.onJobAddedToTask(task);
        blockedJob.setAutoCompleting(true);
        blockedJob.setBlockOnFuture(f, callback);
        currentJob.appendChild(blockedJob);
    }

    public <T> void await(ActorFuture<T> f, Consumer<Throwable> callback)
    {
        await(f, (r, t) ->
        {
            callback.accept(t);
        });
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <T> void awaitAll(Collection<ActorFuture> futures, Consumer<Throwable> callback)
    {
        final int length = futures.size();
        final ActorFuture[] futureArray = futures.toArray(new ActorFuture[length]);

        final Consumer<Throwable>[] callbacks = new Consumer[length];
        callbacks[length - 1] = (t) ->
        {
            for (int i = 0; i < futureArray.length; i++)
            {
                final ActorFuture future = futureArray[i];
                if (future.isCompletedExceptionally())
                {
                    callback.accept(future.getException());
                    return;
                }
            }
            callback.accept(null);
        };

        for (int i = length - 2; i >= 0; i--)
        {
            final int offset = i;
            callbacks[offset] = (t) ->
            {
                await(futureArray[offset + 1], callbacks[offset + 1]);
            };
        }

        await(futureArray[0], callbacks[0]);
    }



    /** can be called by the actor to yield the thread */
    public void yield()
    {
        final ActorJob job = ensureCalledFromWithinActor("yield()");
        job.task.yield();
    }

    public ActorFuture<Void> close()
    {
        final ActorJob closeJob = new ActorJob();

        closeJob.onJobAddedToTask(task);
        closeJob.setAutoCompleting(true);

        closeJob.setRunnable(() ->
        {
            // could be that the task gets auto-closed concurrently.
            if (!task.isClosing)
            {
                task.isClosing = true;
                actor.onActorClosing();
            }
        });

        task.submit(closeJob);

        return task.terminationFuture;
    }

    private void scheduleRunnable(Runnable runnable, boolean autocompleting)
    {
        final ActorTaskRunner currentActorRunner = ensureCalledFromActorRunner("run(...)");
        final ActorJob currentJob = currentActorRunner.getCurrentJob();

        if (currentActorRunner == currentJob.runner)
        {
            /*
             attempt "hot" replace of runnable in the job.
             this is an optimization which allows the job
             to directly execute the next runnable after this runnable completes.
             The optimization is only possible if the current runnable submits exactly one
             additional runnable. If it submits more than one runnable, these runnables
             need to be appended as new jobs to the current job.
             */
            if (currentJob.setRunnable(runnable))
            {
                currentJob.setAutoCompleting(autocompleting);
            }
            else
            {
                final ActorJob job = currentActorRunner.newJob();
                job.setRunnable(runnable);
                job.setAutoCompleting(autocompleting);
                job.onJobAddedToTask(task);
                currentJob.appendChild(job);
            }
        }
        else
        {
            final ActorJob job = currentActorRunner.newJob();
            job.setRunnable(runnable);
            job.setAutoCompleting(autocompleting);
            job.onJobAddedToTask(task);
            task.submit(job);
        }
    }

    public void done()
    {
        final ActorJob job = ensureCalledFromWithinActor("done()");
        job.markDone();
    }

    public boolean isClosing()
    {
        ensureCalledFromWithinActor("isClosing()");
        return task.isClosing;
    }

    private ActorJob ensureCalledFromWithinActor(String methodName)
    {
        final ActorJob currentJob = ensureCalledFromActorRunner(methodName).getCurrentJob();
        if (currentJob == null || currentJob.actor != this.actor)
        {
            throw new UnsupportedOperationException("Incorrect usage of actor." + methodName + ": must only be called from within the actor itself.");
        }

        return currentJob;
    }

    private ActorTaskRunner ensureCalledFromActorRunner(String methodName)
    {
        final ActorTaskRunner runner = ActorTaskRunner.current();

        if (runner == null)
        {
            throw new UnsupportedOperationException("Incorrect usage of actor." + methodName + ": must be called from actor thread");
        }

        return runner;

    }
}
