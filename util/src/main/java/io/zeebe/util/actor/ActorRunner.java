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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.IdleStrategy;

import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.LogUtil;

/**
 * Invokes the given actors in a loop. The amount of invocations depends on the
 * actor's priority. Each actor is invoked up to a given number of times, or
 * until it has no more work to do.
 * <p>
 * Example of one cycle: (baseIterations=10)
 * <pre>
 * Actor    Priority    Invocations (max)
 * ======================================
 * a1       100         100 * 10
 * a2       50          50  * 10
 * a3       1           1   * 10
 * </pre>
 */
public class ActorRunner implements Runnable
{
    private final DeferredCommandContext deferredCommands = new DeferredCommandContext(1024);

    private final List<ActorReferenceImpl> actors = new ArrayList<>();

    private final int baseIterationsPerActor;
    private final IdleStrategy idleStrategy;
    private final ErrorHandler errorHandler;
    private final long samplePeriod;

    private volatile boolean shouldClose = false;

    private long lastSampleTime = -1;
    private final Map<String, String> diagnosticContext;

    public ActorRunner(
            int baseIterationsPerActor,
            IdleStrategy idleStrategy,
            ErrorHandler errorHandler,
            Duration samplePeriod,
            Map<String, String> diagnosticContext)
    {
        this.baseIterationsPerActor = baseIterationsPerActor;
        this.idleStrategy = idleStrategy;
        this.errorHandler = errorHandler;
        this.samplePeriod = samplePeriod.toNanos();
        this.diagnosticContext = diagnosticContext;
    }

    @Override
    public void run()
    {
        LogUtil.doWithMDC(diagnosticContext, this::doWorkUntilClose);
    }

    private void doWorkUntilClose()
    {
        do
        {
            try
            {
                idleStrategy.idle(doWork());
            }
            catch (Throwable e)
            {
                errorHandler.onError(e);
            }
        }
        while (!shouldClose);
    }

    private int doWork()
    {
        int wc = 0;

        wc += deferredCommands.doWork();

        final long now = System.nanoTime();

        boolean sampling = lastSampleTime + samplePeriod < now;
        if (sampling)
        {
            lastSampleTime = now;
        }

        int currentWc = 1;
        for (int p = 0; p < Actor.PRIORITY_HIGH && currentWc > 0; p++)
        {
            currentWc = 0;

            for (int a = 0; a < actors.size(); a++)
            {
                final ActorReferenceImpl actor = actors.get(a);

                if (actor.isClosed())
                {
                    actors.remove(a);
                    a -= 1;
                }
                else
                {
                    final int priority = actor.getActor().getPriority(now);
                    if (priority > p)
                    {
                        currentWc += runActor(actor, now, sampling);
                    }
                }
            }

            wc += currentWc;

            // max one sample per cycle
            sampling = false;
        }

        return wc;
    }

    private int runActor(final ActorReferenceImpl actor, final long now, final boolean sampling)
    {
        int wc = 0;

        try
        {
            if (sampling)
            {
                final long start = System.nanoTime();

                wc += tryRunActor(actor, now);

                final long end = System.nanoTime();
                actor.addDurationSample(end - start);
            }
            else
            {
                wc += tryRunActor(actor, now);
            }

        }
        catch (Throwable e)
        {
            errorHandler.onError(e);
        }

        return wc;
    }

    private int tryRunActor(ActorReferenceImpl actorRef, long now) throws Exception
    {
        int wc = 0;

        final Actor actor = actorRef.getActor();

        for (int i = 0; i < baseIterationsPerActor; i++)
        {
            final int work = actor.doWork();
            wc += work;

            if (work == 0)
            {
                break;
            }
        }

        return wc;
    }

    public void close()
    {
        this.shouldClose = true;
    }

    public void submitActor(ActorReferenceImpl actor)
    {
        deferredCommands.runAsync(() ->
        {
            actors.add(actor);
        });
    }

    public void reclaimActor(ActorReferenceImpl actor, Consumer<ActorReferenceImpl> removeCallback)
    {
        deferredCommands.runAsync(() ->
        {
            if (actors.remove(actor))
            {
                removeCallback.accept(actor);
            }
        });
    }

    public List<ActorReferenceImpl> getActors()
    {
        return actors;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("ActorRunner [currentActor=");
        builder.append(actors);
        builder.append("]");
        return builder.toString();
    }

}
