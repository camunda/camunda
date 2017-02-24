package org.camunda.tngp.broker.clustering.gossip.data;

public class Heartbeat implements Comparable<Heartbeat>
{
    protected long generation;
    protected int version;

    public long generation()
    {
        return generation;
    }

    public Heartbeat generation(final long generation)
    {
        this.generation = generation;
        return this;
    }

    public int version()
    {
        return version;
    }

    public Heartbeat version(final int version)
    {
        this.version = version;
        return this;
    }

    @Override
    public int compareTo(Heartbeat o)
    {
        int cmp = Long.compare(generation(), o.generation());

        if (cmp == 0)
        {
            cmp = Long.compare(version(), o.version());
        }

        return cmp;
    }

    public void wrap(final Heartbeat heartbeat)
    {
        this.generation(heartbeat.generation())
            .version(heartbeat.version());
    }
}
