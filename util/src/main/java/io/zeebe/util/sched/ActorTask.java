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

import static org.agrona.UnsafeAccess.UNSAFE;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

/**
 * A task executed by the scheduler. For each actor (instance), exactly one task is created.
 * Each invocation of one of the actor's methods is an {@link ActorJob}.
 *
 * Tasks are not reusable.
 *
 */
@SuppressWarnings({"restriction", "rawtypes"})
public class ActorTask
{
    private static final long STATE_COUNT_OFFSET;
    private static final long STATE_OFFSET;

    static
    {
        try
        {
            STATE_COUNT_OFFSET = UNSAFE.objectFieldOffset(ActorTask.class.getDeclaredField("stateCount"));
            STATE_OFFSET = UNSAFE.objectFieldOffset(ActorTask.class.getDeclaredField("state"));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public final CompletableActorFuture<Void> terminationFuture = new CompletableActorFuture<>();

    final ZbActor actor;

    private ZbActorScheduler scheduler;

    /** jobs that are submitted to this task externally. A job is submitted "internally" if it is submitted
     * from a job within the same actor while the task is in RUNNING state. */
    final ManyToOneConcurrentLinkedQueue<ActorJob> submittedJobs = new ManyToOneConcurrentLinkedQueue<>();

    private ActorTaskQueueNode[] queueNodes;

    volatile ActorState state = null;

    volatile long stateCount = 0;

    ActorJob currentJob;

    /** the future this task is currently blocked on */
    protected ActorFuture awaitFuture;

    List<ActorSubscription> subscriptions = new ArrayList<>();

    boolean shouldYield;

    public ActorTask(ZbActor actor)
    {
        this.actor = actor;
    }

    volatile boolean isClosing;


    /**
     * called when the task is initially scheduled.
     * @param scheduler
     */
    public void onTaskScheduled(ZbActorScheduler scheduler)
    {
        // reset previous state to allow re-scheduling
        this.terminationFuture.close();
        this.terminationFuture.setAwaitingResult();
        this.isClosing = false;

        this.scheduler = scheduler;
        // create queue nodes
        final int runnerCount = scheduler.runnerCount;
        this.queueNodes = new ActorTaskQueueNode[runnerCount];

        for (int i = 0; i < runnerCount; i++)
        {
            this.queueNodes[i] = new ActorTaskQueueNode(this);
        }

        // create initial job to invoke on start callback
        final ActorJob j = new ActorJob();
        j.setRunnable(actor::onActorStarted);
        j.setAutoCompleting(true);
        j.onJobAddedToTask(this);

        currentJob = j;
    }

    /** Used to externally submit a job. */
    public void submit(ActorJob job)
    {
        submittedJobs.offer(job);

        if (setStateWaitingToWakingUp())
        {
            final ActorTaskRunner current = ActorTaskRunner.current();
            if (current != null)
            {
                current.submit(this);
            }
            else
            {
                scheduler.reSubmitActor(this);
            }
        }
    }

    ActorTaskQueueNode taskQueueNode(int queueId)
    {
        return queueNodes[queueId];
    }

    public boolean execute(ActorTaskRunner runner)
    {
        boolean resubmit = false;

        while (!resubmit && (currentJob != null || poll()))
        {
            if (currentJob.state == ActorState.BLOCKED)
            {
                // check whether the continuation trigger is in the list of submitted jobs
                pollSubmittedJobs();

                if (currentJob.state == ActorState.BLOCKED)
                {
                    break;
                }
            }

            try
            {
                currentJob.execute(runner);
            }
            catch (Exception e)
            {
                // TODO: what to do?
                e.printStackTrace();
            }

            if (shouldYield)
            {
                shouldYield = false;
                resubmit = true;
                break;
            }

            switch (currentJob.state)
            {
                case TERMINATED:

                    final ActorJob terminatedJob = currentJob;
                    currentJob = terminatedJob.getNext();

                    if (terminatedJob.isTriggeredBySubscription())
                    {
                        final ActorSubscription subscription = terminatedJob.getSubscription();

                        if (!subscription.isRecurring())
                        {
                            subscriptions.remove(subscription);
                        }

                        subscription.onJobCompleted();
                    }
                    else
                    {
                        runner.recycleJob(terminatedJob);
                    }

                    break;

                case QUEUED:
                    // the task is experiencing backpressure: do not retry it right now, instead re-enqueue the actor task.
                    // this allows other tasks which may be needed to unblock the backpressure to run
                    resubmit = true;
                    break;

                default:
                    break;
            }
        }

        if (currentJob == null)
        {
            if (subscriptions.size() > 0 && !isClosing)
            {
                resubmit = setStateActiveToWaiting();
            }
            else
            {
                if (isClosing)
                {
                    state = ActorState.TERMINATED;
                    subscriptions.clear();

                    while (submittedJobs.poll() != null)
                    {
                        // discard jobs
                    }

                    terminationFuture.complete(null);
                }
                else
                {
                    if (actor.isAutoClosing())
                    {
                        autoClose(runner);
                        resubmit = true;
                    }
                    else
                    {
                        resubmit = setStateActiveToWaiting();
                    }
                }
            }
        }
        else
        {
            if (currentJob.state == ActorState.BLOCKED)
            {
                resubmit = setStateActiveToBlocked();
            }
        }

        return resubmit;
    }

    private void autoClose(ActorTaskRunner runner)
    {
        final ActorJob closeJob = runner.newJob();

        closeJob.onJobAddedToTask(this);
        closeJob.setAutoCompleting(true);

        closeJob.setRunnable(() ->
        {
            // could be that we both autoclose but also get close job externallly
            if (!isClosing)
            {
                isClosing = true;
                actor.onActorClosing();
            }
        });

        currentJob = closeJob;
    }

    boolean casStateCount(long expectedCount)
    {
        return UNSAFE.compareAndSwapLong(this, STATE_COUNT_OFFSET, expectedCount, expectedCount + 1);
    }

    boolean casState(ActorState expectedState, ActorState newState)
    {
        return UNSAFE.compareAndSwapObject(this, STATE_OFFSET, expectedState, newState);
    }

    boolean claim(long stateCount)
    {
        if (casStateCount(stateCount))
        {
            return true;
        }

        return false;
    }

    /**
     * used to transition from the {@link ActorState#ACTIVE} to the {@link ActorState#WAITING}
     * state
     */
    boolean setStateActiveToWaiting()
    {
        state = ActorState.WAITING;

        /*
         * Accounts for the situation where a job is appended while in state active.
         * In that case the submitting thread does not continue the task since it is not
         * yet in state waiting. After transitioning to waiting we check if we need to wake
         * up right away.
         */
        if (!submittedJobs.isEmpty() || pollSubscriptionsWithoutAddingJobs())
        {
            if (casState(ActorState.WAITING, ActorState.WAKING_UP))
            {
                return true;
            }
        }

        return false;
    }

    boolean setStateActiveToBlocked()
    {
        state = ActorState.BLOCKED;

        if (!submittedJobs.isEmpty())
        {
            if (setStateBlockedToUnblocking())
            {
                return true;
            }
        }

        return false;
    }

    boolean setStateWaitingToWakingUp()
    {
        return casState(ActorState.WAITING, ActorState.WAKING_UP);
    }

    boolean setStateBlockedToUnblocking()
    {
        return casState(ActorState.BLOCKED, ActorState.UNBLOCKING);
    }

    private boolean poll()
    {
        boolean result = false;

        result |= pollSubmittedJobs();
        result |= pollSubscriptions();

        return result;
    }

    private boolean pollSubscriptions()
    {
        if (isClosing)
        {
            return false;
        }

        boolean hasJobs = false;

        for (int i = 0; i < subscriptions.size(); i++)
        {
            final ActorSubscription subscription = subscriptions.get(i);

            if (subscription.poll())
            {
                final ActorJob job = subscription.getJob();
                job.state = ActorState.QUEUED;

                if (currentJob == null)
                {
                    currentJob = job;
                }
                else
                {
                    currentJob.append(job);
                }

                hasJobs = true;
            }

        }
        return hasJobs;
    }

    private boolean pollSubscriptionsWithoutAddingJobs()
    {
        if (isClosing)
        {
            return false;
        }

        boolean result = false;

        for (int i = 0; i < subscriptions.size() && !result; i++)
        {
            result |= subscriptions.get(i).poll();
        }

        return result;
    }

    private boolean pollSubmittedJobs()
    {
        boolean hasJobs = false;

        while (!submittedJobs.isEmpty())
        {
            final ActorJob job = submittedJobs.poll();
            if (job != null)
            {
                if (currentJob == null)
                {
                    currentJob = job;
                }
                else
                {
                    currentJob.append(job);

                    if (currentJob.state == ActorState.BLOCKED && job.isContinuationSignal(awaitFuture))
                    {
                        // continuation signal has been received, the blocked job can be removed
                        currentJob = currentJob.getNext();
                    }
                }
                hasJobs = true;
            }
        }

        return hasJobs;
    }

    public ActorState getState()
    {
        return state;
    }

    void onFutureCompleted(ActorJob continuationJob)
    {
        submittedJobs.offer(continuationJob);

        if (setStateBlockedToUnblocking())
        {
            ActorTaskRunner.current().submit(this);
        }
    }

    @Override
    public String toString()
    {
        return actor.getName() + " " + state;
    }

    public void addSubscription(ActorSubscription subscription)
    {
        subscriptions.add(subscription);
    }

    public void yield()
    {
        shouldYield = true;
    }

    public boolean tryWakeup()
    {
        return setStateWaitingToWakingUp();
    }

    public ZbActorScheduler getScheduler()
    {
        return scheduler;
    }
}
