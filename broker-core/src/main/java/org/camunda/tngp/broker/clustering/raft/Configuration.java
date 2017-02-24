package org.camunda.tngp.broker.clustering.raft;

import java.util.List;

public class Configuration
{
    private long configurationEntryPosition;
    private int configurationEntryTerm;
    private volatile List<Member> members;

    public Configuration(final long configurationEntryPosition, final int configurationEntryTerm, final List<Member> members)
    {
        this.configurationEntryPosition = configurationEntryPosition;
        this.configurationEntryTerm = configurationEntryTerm;
        this.members = members;
    }

    public long configurationEntryPosition()
    {
        return configurationEntryPosition;
    }

    public Configuration configurationEntryPosition(final long configurationEntryPosition)
    {
        this.configurationEntryPosition = configurationEntryPosition;
        return this;
    }

    public int configurationEntryTerm()
    {
        return configurationEntryTerm;
    }

    public Configuration configurationEntryTerm(final int configurationEntryTerm)
    {
        this.configurationEntryTerm = configurationEntryTerm;
        return this;
    }

    public List<Member> members()
    {
        return members;
    }

    public Configuration members(final List<Member> members)
    {
        this.members = members;
        return this;
    }
}
