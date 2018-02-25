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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.metrics.TaskMetrics;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

/**
 * A task executed by the scheduler. For each actor (instance), exactly one task is created.
 * Each invocation of one of the actor's methods is an {@link ActorJob}.
 */
@SuppressWarnings("restriction")
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

    private ActorExecutor actorExecutor;
    private ActorThreadGroup actorThreadGroup;

    /** jobs that are submitted to this task externally. A job is submitted "internally" if it is submitted
     * from a job within the same actor while the task is in RUNNING state. */
    final ManyToOneConcurrentLinkedQueue<ActorJob> submittedJobs = new ManyToOneConcurrentLinkedQueue<>();

    volatile ActorState state = null;

    volatile long stateCount = 0;

    ActorJob currentJob;

    private ActorSubscription[] subscriptions = new ActorSubscription[0];

    boolean shouldYield;

    boolean isClosing;

    private TaskMetrics taskMetrics;

    private boolean isCollectTaskMetrics;

    private boolean isJumbo = false;

    /** the priority class of the task. Only set if the task is scheduled as non-blocking, CPU-bound */
    private int priority = ActorPriority.REGULAR.getPriorityClass();

    /** the id of the io device used. Only set if this task is scheduled as a blocking io task*/
    private int deviceId;

    public ActorTask(ZbActor actor)
    {
        this.actor = actor;
    }

    /**
     * called when the task is initially scheduled.
     */
    public void onTaskScheduled(ActorExecutor actorExecutor, ActorThreadGroup actorThreadGroup, TaskMetrics taskMetrics)
    {
        this.actorExecutor = actorExecutor;
        this.actorThreadGroup = actorThreadGroup;
        // reset previous state to allow re-scheduling
        this.terminationFuture.close();
        this.terminationFuture.setAwaitingResult();
        this.isClosing = false;
        this.isJumbo = false;

        this.isCollectTaskMetrics = taskMetrics != null;
        this.taskMetrics = taskMetrics;

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
        // add job to queue
        submittedJobs.offer(job);
        // wakeup task if waiting
        tryWakeup();
    }

    public boolean execute(ActorThread runner)
    {
        state = ActorState.ACTIVE;

        boolean resubmit = false;

        while (!resubmit && (currentJob != null || poll()))
        {
            try
            {
                currentJob.execute(runner);
            }
            catch (Exception e)
            {
                // TODO: what to do?
                e.printStackTrace();
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
                            removeSubscription(subscription);
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

            if (shouldYield)
            {
                shouldYield = false;
                resubmit = currentJob != null;
                break;
            }
        }

        if (currentJob == null)
        {
            if (subscriptions.length > 0 && !isClosing)
            {
                resubmit = setStateActiveToWaiting();
            }
            else
            {
                if (isClosing)
                {
                    cleanUpOnClose();
                }
                else
                {
                    autoClose(runner);
                    resubmit = true;
                }
            }
        }

        return resubmit;
    }

    private void cleanUpOnClose()
    {
        state = ActorState.TERMINATED;
        subscriptions = new ActorSubscription[0];

        while (submittedJobs.poll() != null)
        {
            // discard jobs
        }

        if (taskMetrics != null)
        {
            taskMetrics.close();
        }

        terminationFuture.complete(null);
    }

    private void autoClose(ActorThread runner)
    {
        final ActorJob closeJob = runner.newJob();

        closeJob.onJobAddedToTask(this);
        closeJob.setAutoCompleting(true);

        closeJob.setRunnable(this::closingBehavior);

        currentJob = closeJob;
    }

    public void closingBehavior()
    {
        // could be that we both autoclose but also get close job externallly
        if (!isClosing)
        {
            isClosing = true;

            subscriptions = new ActorSubscription[0];

            while (submittedJobs.poll() != null)
            {
                // discard jobs
            }
            ActorThread.current().getCurrentJob().next = null;

            actor.onActorClosing();
        }
    }

    boolean casStateCount(long expectedCount)
    {
        return UNSAFE.compareAndSwapLong(this, STATE_COUNT_OFFSET, expectedCount, expectedCount + 1);
    }

    boolean casState(ActorState expectedState, ActorState newState)
    {
        return UNSAFE.compareAndSwapObject(this, STATE_OFFSET, expectedState, newState);
    }

    public boolean claim(long stateCount)
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
        // take copy of subscriptions list: once we set the state to WAITING, the task could be woken up by another
        // thread. That thread could modify the subscriptions array.
        final ActorSubscription[] subscriptionsCopy = this.subscriptions;

        // first set state to waiting
        state = ActorState.WAITING;

        /*
         * Accounts for the situation where a job is appended while in state active.
         * In that case the submitting thread does not continue the task since it is not
         * yet in state waiting. After transitioning to waiting we check if we need to wake
         * up right away.
         */
        if (!submittedJobs.isEmpty() || pollSubscriptionsWithoutAddingJobs(subscriptionsCopy))
        {
            // could be that another thread already woke up this task
            if (casState(ActorState.WAITING, ActorState.WAKING_UP))
            {
                return true;
            }
        }

        return false;
    }

    public boolean tryWakeup()
    {
        boolean didWakeup = false;

        if (casState(ActorState.WAITING, ActorState.WAKING_UP))
        {
            actorThreadGroup.submit(this);
            didWakeup = true;
        }

        return didWakeup;
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

        for (int i = 0; i < subscriptions.length; i++)
        {
            final ActorSubscription subscription = subscriptions[i];

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

    private boolean pollSubscriptionsWithoutAddingJobs(ActorSubscription[] subscriptions)
    {
        if (isClosing)
        {
            return false;
        }

        boolean result = false;

        for (int i = 0; i < subscriptions.length && !result; i++)
        {
            result |= subscriptions[i].poll();
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

    @Override
    public String toString()
    {
        return actor.getName() + " " + state;
    }

    public void yield()
    {
        shouldYield = true;
    }

    public TaskMetrics getMetrics()
    {
        return taskMetrics;
    }

    public boolean isCollectTaskMetrics()
    {
        return isCollectTaskMetrics;
    }

    public void reportExecutionTime(long t)
    {
        taskMetrics.reportExecutionTime(t);
    }

    public void warnMaxTaskExecutionTimeExceeded(long taskExecutionTime)
    {
        if (!isJumbo)
        {
            isJumbo = true;

            System.err.println(String.format("%s reported running for %dµs. Jumbo task detected! " +
                    "Will not print further warnings for this task.",
                    actor.getName(), TimeUnit.NANOSECONDS.toMicros(taskExecutionTime)));
        }
    }

    public boolean isHasWarnedJumbo()
    {
        return isJumbo;
    }

    public long getStateCount()
    {
        return stateCount;
    }

    public ActorThreadGroup getActorThreadGroup()
    {
        return actorThreadGroup;
    }

    public String getName()
    {
        return actor.getName();
    }

    public ZbActor getActor()
    {
        return actor;
    }

    public boolean isClosing()
    {
        return isClosing;
    }

    public int getPriority()
    {
        return priority;
    }

    public void setPriority(int priority)
    {
        this.priority = priority;
    }

    public int getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(int deviceId)
    {
        this.deviceId = deviceId;
    }

    public ActorExecutor getActorExecutor()
    {
        return actorExecutor;
    }

    // subscription helpers

    public void addSubscription(ActorSubscription subscription)
    {
        final ActorSubscription[] arrayCopy = Arrays.copyOf(subscriptions, subscriptions.length + 1);
        arrayCopy[arrayCopy.length - 1] = subscription;
        subscriptions = arrayCopy;
    }

    private void removeSubscription(ActorSubscription subscription)
    {
        final int length = subscriptions.length;

        int index = -1;
        for (int i = 0; i < subscriptions.length; i++)
        {
            if (subscriptions[i] == subscription)
            {
                index = i;
            }
        }

        final ActorSubscription[] newSubscriptions = new ActorSubscription[length - 1];
        System.arraycopy(subscriptions, 0, newSubscriptions, 0, index);
        if (index < length - 1)
        {
            System.arraycopy(subscriptions, index + 1, newSubscriptions, index, length - index - 1);
        }

        this.subscriptions = newSubscriptions;
    }
}
