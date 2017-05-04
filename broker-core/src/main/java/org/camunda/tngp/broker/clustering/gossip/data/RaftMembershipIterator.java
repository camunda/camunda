package org.camunda.tngp.broker.clustering.gossip.data;

import java.util.Iterator;


public class RaftMembershipIterator implements Iterator<RaftMembership>
{

    protected final RaftMembershipList list;
    protected int position = -1;

    public RaftMembershipIterator(final RaftMembershipList list)
    {
        this.list = list;
    }

    @Override
    public boolean hasNext()
    {
        return position + 1 < list.size();
    }

    @Override
    public RaftMembership next()
    {
        if (!hasNext())
        {
            throw new java.util.NoSuchElementException();
        }

        position++;

        return list.get(position);
    }

    public RaftMembershipIterator reset()
    {
        position = -1;

        return this;
    }

}
