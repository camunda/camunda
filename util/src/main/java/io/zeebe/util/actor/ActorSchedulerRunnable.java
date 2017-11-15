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
package io.zeebe.util.actor;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * The scheduler tries to balance the workload between the given runners
 * periodically. The workload of an actor (i.e. the duration of one duty cycle
 * in nano seconds) is measured by the runner and stored in the reference. In
 * order to balance the work, it looks for the actor with the highest workload
 * which can be moved from a runner with low workload to a runner with higher
 * workload and not result in a new imbalance (actor workload <= runner workload
 * difference / 2).
 */
public class ActorSchedulerRunnable implements Runnable
{
    private final ConcurrentLinkedQueue<ActorReferenceImpl> unclaimedActors = new ConcurrentLinkedQueue<>();

    private final Object monitor = new Object();

    private volatile boolean closed = false;

    private final Function<Actor, ActorReferenceImpl> actorRefFactory;

    private final ActorRunner[] runners;
    private int nextRunner = 0;

    private final double[][] durationsPerRunner;
    private final double[] aggregatedRunnerDurations;

    private final double imbalanceThreshold;

    private final long maxBackoff;
    private final long initialBackoff;

    private long waitTime;
    private long nextSchedulingTime = -1;

    public ActorSchedulerRunnable(ActorRunner[] runners, Function<Actor, ActorReferenceImpl> actorRefFactory, double imbalanceThreshold,
            Duration initialBackoff, Duration maxBackoff)
    {
        this.runners = runners;
        this.actorRefFactory = actorRefFactory;

        this.durationsPerRunner = new double[runners.length][];
        this.aggregatedRunnerDurations = new double[runners.length];

        this.imbalanceThreshold = imbalanceThreshold;

        this.initialBackoff = initialBackoff.toMillis();
        this.maxBackoff = maxBackoff.toMillis();
    }

    public ActorReference schedule(Actor actor)
    {
        synchronized (monitor)
        {
            final ActorReferenceImpl actorRef = actorRefFactory.apply(actor);

            unclaimedActors.add(actorRef);

            monitor.notify();

            return actorRef;
        }
    }

    public void close()
    {
        synchronized (monitor)
        {
            this.closed = true;

            monitor.notify();
        }
    }

    @Override
    public void run()
    {
        while (!closed)
        {
            try
            {
                doWork();

                synchronized (monitor)
                {
                    if (unclaimedActors.isEmpty())
                    {
                        monitor.wait(waitTime);
                    }
                }
            }
            catch (InterruptedException e)
            {
                // ignore
            }
        }
    }

    private void doWork()
    {
        if (runners.length > 1)
        {
            final long now = System.currentTimeMillis();
            if (nextSchedulingTime < now)
            {
                final boolean didBalance = balanceRunners();

                waitTime = didBalance ? initialBackoff : maxBackoff;
                nextSchedulingTime = now + waitTime;
            }
        }

        ActorReferenceImpl unclaimedActor = null;
        while ((unclaimedActor = unclaimedActors.poll()) != null)
        {
            claimActor(unclaimedActor);
        }
    }

    private boolean balanceRunners()
    {
        boolean didBalance = false;

        // collect and aggregate durations
        for (int r = 0; r < runners.length; r++)
        {
            final ActorRunner runner = runners[r];
            final List<ActorReferenceImpl> actors = runner.getActors();

            double sum = 0;
            durationsPerRunner[r] = new double[actors.size()];

            for (int a = 0; a < durationsPerRunner[r].length; a++)
            {
                final ActorReferenceImpl actor = actors.get(a);
                if (actor != null)
                {
                    final double duration = actor.getDuration();

                    sum += duration;
                    durationsPerRunner[r][a] = duration;
                }
            }

            aggregatedRunnerDurations[r] = sum;
        }

        final int[] sortedRunners = sortRunnersByDuration(aggregatedRunnerDurations);

        // balance workload between runners
        for (int r = 0; r < sortedRunners.length / 2; r++)
        {
            final int low = sortedRunners[r];
            final int high = sortedRunners[sortedRunners.length - 1 - r];

            didBalance |= balanceRunners(low, high);
        }

        // the runner with the lowest load claim the next actor
        nextRunner = sortedRunners[0];

        return didBalance;
    }

    private int[] sortRunnersByDuration(double[] runnerDurations)
    {
        // insert-sort
        final int[] sortedRunners = new int[runners.length];
        sortedRunners[0] = 0;

        for (int r = 1; r < runners.length; r++)
        {
            final double duration = runnerDurations[r];

            int i = r;
            while (i > 0 && runnerDurations[sortedRunners[i - 1]] > duration)
            {
                sortedRunners[i] = sortedRunners[i - 1];
                i -= 1;
            }

            sortedRunners[i] = r;
        }
        return sortedRunners;
    }

    private boolean balanceRunners(int low, int high)
    {
        boolean didBalance = false;

        if (runners[high].getActors().size() > 1)
        {
            final double durationDiff = aggregatedRunnerDurations[high] - aggregatedRunnerDurations[low];
            final double imbalance = durationDiff > 0 ? ((double) durationDiff / (aggregatedRunnerDurations[high] + aggregatedRunnerDurations[low])) : 0.0;
            if (imbalance >= imbalanceThreshold)
            {
                // find a suitable actor to balance the workload
                final double[] durations = durationsPerRunner[high];

                int actor = -1;
                double actorDuration = -1F;

                for (int a = 0; a < durations.length; a++)
                {
                    final double duration = durations[a];
                    if (duration > actorDuration && duration <= durationDiff / 2)
                    {
                        actor = a;
                        actorDuration = duration;
                    }
                }

                if (actor >= 0)
                {
                    final ActorReferenceImpl actorRef = runners[high].getActors().get(actor);

                    runners[high].reclaimActor(actorRef, runners[low]::submitActor);

                    didBalance = true;
                }
            }
        }
        return didBalance;
    }

    private void claimActor(ActorReferenceImpl actor)
    {
        final ActorRunner runner = runners[nextRunner];
        runner.submitActor(actor);

        // use round-robin to spread actors initially
        nextRunner = (nextRunner + 1) % runners.length;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("ActorSchedulerRunnable [runners=");
        builder.append(Arrays.toString(runners));
        builder.append("]");
        return builder.toString();
    }

}
