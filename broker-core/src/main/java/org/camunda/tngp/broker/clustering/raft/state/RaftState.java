package org.camunda.tngp.broker.clustering.raft.state;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.Raft.State;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureRequest;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureResponse;
import org.camunda.tngp.broker.clustering.raft.message.JoinRequest;
import org.camunda.tngp.broker.clustering.raft.message.JoinResponse;
import org.camunda.tngp.broker.clustering.raft.message.LeaveRequest;
import org.camunda.tngp.broker.clustering.raft.message.LeaveResponse;
import org.camunda.tngp.broker.clustering.raft.message.PollRequest;
import org.camunda.tngp.broker.clustering.raft.message.PollResponse;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.transport.SocketAddress;

public abstract class RaftState
{
    protected final RaftContext context;
    protected final Raft raft;
    protected final LogStreamState logStreamState;

    protected final PollResponse pollResponse;
    protected final VoteResponse voteResponse;
    protected final AppendResponse appendResponse;
    protected final JoinResponse joinResponse;
    protected final LeaveResponse leaveResponse;
    protected final ConfigureResponse configureResponse;

    public RaftState(final RaftContext context)
    {
        this.context = context;
        this.raft = context.getRaft();
        this.logStreamState = context.getLogStreamState();
        this.pollResponse = new PollResponse();
        this.voteResponse = new VoteResponse();
        this.appendResponse = new AppendResponse();
        this.joinResponse = new JoinResponse();
        this.leaveResponse = new LeaveResponse();
        this.configureResponse = new ConfigureResponse();
    }

    protected boolean updateTermAndLeader(final int term, final Member leader)
    {
        final int currentTerm = raft.term();
        final SocketAddress currentLeader = raft.leader();

        if (term > currentTerm || (term == currentTerm && currentLeader == null && leader != null))
        {
            raft.term(term);
            raft.leader(leader != null ? leader.endpoint() : null);
            return true;
        }

        return false;
    }

    public abstract void open();

    public abstract void close();

    public abstract int doWork();

    public abstract boolean isClosed();

    public abstract State state();

    public abstract PollResponse poll(PollRequest pollRequest);

    public abstract VoteResponse vote(VoteRequest voteRequest);

    public abstract AppendResponse append(AppendRequest appendRequest);

    public abstract void appended(AppendResponse appendResponse);

    public abstract ConfigureResponse configure(ConfigureRequest configureRequest);

    public abstract CompletableFuture<JoinResponse> join(JoinRequest joinRequest);

    public abstract CompletableFuture<LeaveResponse> leave(LeaveRequest leaveRequest);

}
