package org.camunda.tngp.util.actor;

import static org.agrona.BitUtil.findNextPositivePowerOfTwo;
import static org.agrona.BitUtil.next;

public class ActorReferenceImpl implements ActorReference
{
    private final Actor actor;

    private volatile boolean shouldClose = false;

    // metrics
    private final long[] durationSamples;
    private final int durationSamplesCapacity;

    private int nextDurationIndex = 0;
    private int durationSampleSize = 0;
    private volatile long durationAvg = 0;


    public ActorReferenceImpl(Actor actor, int durationSampleCount)
    {
        this.actor = actor;
        durationSamplesCapacity = findNextPositivePowerOfTwo(durationSampleCount);
        this.durationSamples = new long[durationSamplesCapacity];
    }

    public Actor getActor()
    {
        return actor;
    }

    @Override
    public void close()
    {
        shouldClose = true;
    }

    public boolean isClosed()
    {
        return shouldClose;
    }

    public void addDurationSample(long duration)
    {
        if (durationSampleSize < durationSamplesCapacity)
        {
            durationAvg = (durationAvg * durationSampleSize + duration) / (durationSampleSize + 1);

            durationSampleSize += 1;
        }
        else
        {
            final long oldestDuration = durationSamples[nextDurationIndex];
            durationAvg = (durationAvg * durationSampleSize - oldestDuration + duration) / durationSampleSize;
        }

        durationSamples[nextDurationIndex] = duration;
        nextDurationIndex = next(nextDurationIndex, durationSamplesCapacity);
    }

    public long getDuration()
    {
        return durationAvg;
    }

    @Override
    public String name()
    {
        return actor.name();
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("ActorReference [actor=");
        builder.append(actor);
        builder.append(", durationAvg=");
        builder.append(durationAvg);
        builder.append("]");
        return builder.toString();
    }

}
