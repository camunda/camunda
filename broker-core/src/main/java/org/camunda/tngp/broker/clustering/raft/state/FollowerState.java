package org.camunda.tngp.broker.clustering.raft.state;

import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft.State;

public class FollowerState extends ActiveState
{
    protected static final int STATE_CLOSED = 0;
    protected static final int STATE_OPEN = 1;

    protected int state = STATE_CLOSED;

    protected long heartbeatTimeoutConfig = 350L;
    protected long heartbeatTimeout = -1L;

    public FollowerState(final Raft raft, final LogStreamState logStreamState)
    {
        super(raft, logStreamState);
    }

    @Override
    public State state()
    {
        return Raft.State.FOLLOWER;
    }


    @Override
    public void doOpen()
    {
        heartbeatTimeout = randomTimeout(heartbeatTimeoutConfig);
        raft.lastContact(System.currentTimeMillis());
    }

    @Override
    public void doClose()
    {
        heartbeatTimeout = -1L;
    }

    @Override
    protected VoteResponse handleVoteRequest(final VoteRequest request)
    {
        final VoteResponse voteResponse = super.handleVoteRequest(request);
        if (voteResponse.granted())
        {
            raft.lastContact(System.currentTimeMillis());
            heartbeatTimeout = randomTimeout(heartbeatTimeoutConfig);
        }
        return voteResponse;
    }

    @Override
    public int doWork()
    {
        int workcount = 0;

        if (isOpen())
        {
            final long lastContact = raft.lastContact();

            final long now = System.currentTimeMillis();

            if (now >= (lastContact + heartbeatTimeout))
            {
//                System.out.println("timeout");
//                System.out.println("now: " + now);
//                System.out.println("lastContact + heartbeatTimeout: " + (lastContact + heartbeatTimeout));
                raft.transition(Raft.State.CANDIDATE);
                workcount += 1;
            }
        }

        return workcount;
    }


    @Override
    public AppendResponse append(final AppendRequest request)
    {
        raft.lastContact(System.currentTimeMillis());
        heartbeatTimeout = randomTimeout(heartbeatTimeoutConfig);

//        System.out.println("\n");
//        System.out.println(">>> index: " + request.index());
//        System.out.println(">>> lastContact: " + raft.lastContact());
//        System.out.println(">>> heartbeatTimeout: " + heartbeatTimeout);

        return super.append(request);
    }
}
