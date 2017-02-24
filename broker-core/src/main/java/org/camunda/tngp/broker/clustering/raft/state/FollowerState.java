package org.camunda.tngp.broker.clustering.raft.state;

import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.Raft.State;
import org.camunda.tngp.broker.clustering.raft.RaftContext;

public class FollowerState extends ActiveState
{
    private boolean open;

    private long heartbeatTimeoutConfig = 350L;
    private long heartbeatTimeout = -1L;

    public FollowerState(final RaftContext context)
    {
        super(context);
    }

    @Override
    public void open()
    {
        context.getLogStreamState().reset();
        raft.lastContact(System.currentTimeMillis());
        heartbeatTimeout = randomTimeout(heartbeatTimeoutConfig);
        open = true;
    }

    @Override
    public void close()
    {
        heartbeatTimeout = -1L;
        open = false;
    }

    @Override
    public boolean isClosed()
    {
        return !open;
    }

    @Override
    public int doWork()
    {
        int workcount = 0;

        final long current = System.currentTimeMillis();
        if (current >= (raft.lastContact() + heartbeatTimeout))
        {
            raft.transition(Raft.State.CANDIDATE);
            workcount += 1;
        }

        return workcount;
    }

    @Override
    public State state()
    {
        return Raft.State.FOLLOWER;
    }
}
