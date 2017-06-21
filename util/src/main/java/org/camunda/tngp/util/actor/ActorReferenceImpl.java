package org.camunda.tngp.util.actor;

public class ActorReferenceImpl implements ActorReference
{
    private final Actor actor;

    private volatile boolean shouldClose = false;

    // metrics
    private final long[] durationSamples;

    private int nextDurationIndex = 0;
    private int durationSampleSize = 0;
    private volatile long durationAvg = 0;

    public ActorReferenceImpl(Actor actor, int durationSampleCount)
    {
        this.actor = actor;
        this.durationSamples = new long[durationSampleCount];
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
        durationSamples[nextDurationIndex] = duration;

        durationSampleSize = Math.min(durationSampleSize + 1, durationSamples.length);
        nextDurationIndex = (nextDurationIndex + 1) % durationSamples.length;

        // update average
        int sum = 0;
        for (int i = 0; i < durationSampleSize; i++)
        {
            sum += durationSamples[i];
        }
        durationAvg = sum / durationSampleSize;
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
