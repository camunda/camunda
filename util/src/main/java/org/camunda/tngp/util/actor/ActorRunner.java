package org.camunda.tngp.util.actor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.IdleStrategy;
import org.camunda.tngp.util.DeferredCommandContext;

public class ActorRunner implements Runnable
{
    private final DeferredCommandContext deferredCommands = new DeferredCommandContext(1024);

    private final List<ActorReferenceImpl> currentActor = new ArrayList<>();

    private final int baseIterationsPerActor;
    private final IdleStrategy idleStrategy;
    private final ErrorHandler errorHandler;
    private final long samplePeriod;

    private volatile boolean shouldClose = false;

    private long lastSampleTime = -1;

    public ActorRunner(int baseIterationsPerActor, IdleStrategy idleStrategy, ErrorHandler errorHandler, Duration samplePeriod)
    {
        this.baseIterationsPerActor = baseIterationsPerActor;
        this.idleStrategy = idleStrategy;
        this.errorHandler = errorHandler;
        this.samplePeriod = samplePeriod.toNanos();
    }

    @Override
    public void run()
    {
        do
        {
            try
            {
                final boolean didWork = doWork();

                if (didWork)
                {
                    idleStrategy.reset();
                }
                else
                {
                    idleStrategy.idle();
                }
            }
            catch (Throwable e)
            {
                errorHandler.onError(e);
            }
        }
        while (!shouldClose);
    }

    private boolean doWork()
    {
        deferredCommands.doWork();

        final long now = System.nanoTime();

        final boolean sampling = lastSampleTime + samplePeriod < now;
        if (sampling)
        {
            lastSampleTime = now;
        }

        boolean didWork = false;

        for (int i = 0; i < currentActor.size(); i++)
        {
            final ActorReferenceImpl actor = currentActor.get(i);

            if (actor.isClosed())
            {
                currentActor.remove(i);
                i -= 1;
            }
            else
            {
                didWork = runActor(actor, now, sampling);
            }
        }
        return didWork;
    }

    private boolean runActor(final ActorReferenceImpl actor, final long now, final boolean sampling)
    {
        boolean didWork = false;

        try
        {
            if (sampling)
            {
                final long start = System.nanoTime();

                didWork = tryRunActor(actor, now);

                final long end = System.nanoTime();
                actor.addDurationSample(end - start);
            }
            else
            {
                didWork = tryRunActor(actor, now);
            }

        }
        catch (Throwable e)
        {
            errorHandler.onError(e);
        }
        return didWork;
    }

    private boolean tryRunActor(ActorReferenceImpl actorRef, long now) throws Exception
    {
        final Actor actor = actorRef.getActor();
        final int priority = actor.getPriority(now);
        final int maxIterations = priority * baseIterationsPerActor;

        int i = 0;
        for (; i < maxIterations; i++)
        {
            final boolean didWork = actor.doWork() > 0;

            if (!didWork)
            {
                break;
            }
        }
        return i > 0;
    }

    public void close()
    {
        this.shouldClose = true;
    }

    public void submitActor(ActorReferenceImpl actor)
    {
        deferredCommands.runAsync(() ->
        {
            currentActor.add(actor);
        });
    }

    public void reclaimActor(ActorReferenceImpl actor, Consumer<ActorReferenceImpl> removeCallback)
    {
        deferredCommands.runAsync(() ->
        {
            currentActor.remove(actor);
            removeCallback.accept(actor);
        });
    }

    public List<ActorReferenceImpl> getActors()
    {
        return currentActor;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("ActorRunner [currentActor=");
        builder.append(currentActor);
        builder.append("]");
        return builder.toString();
    }

}
