package org.camunda.tngp.broker.clustering.raft.state;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
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
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.util.MessageWriter;

public abstract class RaftState
{
    protected final RaftContext context;
    protected final Raft raft;
    protected final LogStreamState logStreamState;

    protected final VoteRequest voteRequest;
    protected final VoteResponse voteResponse;

    protected final AppendRequest appendRequest;
    protected final AppendResponse appendResponse;

    protected final JoinRequest joinRequest;
    protected final JoinResponse joinResponse;

    protected final ConfigureRequest configureRequest;
    protected final ConfigureResponse configureResponse;

    protected final MessageWriter messageWriter;

    public RaftState(final RaftContext context)
    {
        this.context = context;
        this.raft = context.getRaft();
        this.logStreamState = context.getLogStreamState();

        this.voteRequest = new VoteRequest();
        this.voteResponse = new VoteResponse();

        this.appendRequest = new AppendRequest();
        this.appendResponse = new AppendResponse();

        this.joinRequest = new JoinRequest();
        this.joinResponse = new JoinResponse();

        this.configureRequest = new ConfigureRequest();
        this.configureResponse = new ConfigureResponse();

        this.messageWriter = new MessageWriter(context.getSendBuffer());
    }

    protected boolean updateTermAndLeader(final int term, final Member leader)
    {
        final int currentTerm = raft.term();
        final Endpoint currentLeader = raft.leader();

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

    public abstract int onVoteRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connection, final long requestId);

    public abstract int onAppendRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId);

    public abstract int onAppendResponse(final DirectBuffer buffer, final int offset, final int length);

    public abstract int onJoinRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connectionId, final long requestId);

    public abstract int onConfigureRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connectionId, final long requestId);

}
