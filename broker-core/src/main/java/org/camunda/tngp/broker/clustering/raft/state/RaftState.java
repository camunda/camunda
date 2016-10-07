package org.camunda.tngp.broker.clustering.raft.state;

import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureRequest;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureResponse;
import org.camunda.tngp.broker.clustering.raft.message.JoinRequest;
import org.camunda.tngp.broker.clustering.raft.message.JoinResponse;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft.State;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public abstract class RaftState
{
    protected final Raft raft;
    protected final LogStreamState logStreamState;

    protected boolean open;

    protected final JoinResponse joinResponse = new JoinResponse();
    protected final ConfigureResponse configureResponse = new ConfigureResponse();
    protected final VoteResponse voteResponse = new VoteResponse();
    protected final AppendResponse appendResponse = new AppendResponse();

    public RaftState(final Raft raft, final LogStreamState logStreamState)
    {
        this.raft = raft;
        this.logStreamState = logStreamState;
    }

    public void open()
    {
        if (logStreamState != null)
        {
            logStreamState.reset();
        }

        open = true;
        doOpen();
    }

    public void doOpen()
    {
        // noop;
    }

    public void close()
    {
        doClose();
        open = false;
    }

    public void doClose()
    {
        // noop;
    }

    public boolean isOpen()
    {
        return open;
    }

    protected boolean updateTermAndLeader(final int term, final Endpoint leader)
    {
        final int currentTerm = raft.term();
        final Endpoint currentLeader = raft.leader();

        if (term > currentTerm || (term == currentTerm && currentLeader == null && leader != null))
        {
            raft.term(term);
            raft.leader(leader);
            return true;
        }

        return false;
    }

    public int doWork()
    {
        return 0;
    }

    public abstract State state();

    public abstract void join(final JoinRequest joinRequest, final DeferredResponse response);

    public abstract ConfigureResponse configure(final ConfigureRequest configureRequest);

    public abstract VoteResponse vote(final VoteRequest voteRequest);

    public abstract AppendResponse append(final AppendRequest appendRequest);

}
