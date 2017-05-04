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

    public void wrap(final Heartbeat heartbeat)
    {
        this.generation(heartbeat.generation())
            .version(heartbeat.version());
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

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final Heartbeat heartbeat = (Heartbeat) o;

        if (generation != heartbeat.generation)
        {
            return false;
        }
        return version == heartbeat.version;
    }

    @Override
    public int hashCode()
    {
        int result = (int) (generation ^ (generation >>> 32));
        result = 31 * result + version;
        return result;
    }

    @Override
    public String toString()
    {
        return "Heartbeat{" +
            "generation=" + generation +
            ", version=" + version +
            '}';
    }

}
