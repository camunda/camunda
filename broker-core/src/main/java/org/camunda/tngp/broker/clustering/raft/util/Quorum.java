package org.camunda.tngp.broker.clustering.raft.util;

public class Quorum
{
    private int quorum;
    private int succeeded = 1;
    private int failed;
    private boolean complete;
    private boolean stepdown;

    public void open(final int quorum)
    {
        this.quorum = quorum;

        succeeded = 1;
        failed = 0;
        stepdown = false;
    }

    public void close()
    {
        complete = false;
    }

    protected void checkComplete()
    {
        if (!complete && (succeeded >= quorum || failed >= quorum))
        {
            complete = true;
        }
    }

    public Quorum succeed()
    {
        succeeded++;
        checkComplete();
        return this;
    }

    public Quorum fail()
    {
        failed++;
        checkComplete();
        return this;
    }

    public Quorum stepdown()
    {
        stepdown = true;
        complete = true;
        return this;
    }

    public boolean isCompleted()
    {
        return complete;
    }

    public boolean isElected()
    {
        return !stepdown && succeeded >= quorum;
    }

}
