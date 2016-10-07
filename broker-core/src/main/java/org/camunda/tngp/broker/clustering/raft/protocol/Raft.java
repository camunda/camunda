package org.camunda.tngp.broker.clustering.raft.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.JoinResponse;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.controller.JoinController;
import org.camunda.tngp.broker.clustering.raft.state.ActiveState;
import org.camunda.tngp.broker.clustering.raft.state.CandidateState;
import org.camunda.tngp.broker.clustering.raft.state.FollowerState;
import org.camunda.tngp.broker.clustering.raft.state.InactiveState;
import org.camunda.tngp.broker.clustering.raft.state.LeaderState;
import org.camunda.tngp.broker.clustering.raft.state.LogStreamState;
import org.camunda.tngp.broker.clustering.raft.state.RaftState;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.singlemessage.DataFramePool;

public class Raft
{
    protected final InactiveState inactiveState;
    protected final LeaderState leaderState;
    protected final CandidateState candidateState;
    protected final FollowerState followerState;

    protected final RaftContext context;

    protected final LogStream stream;

    protected volatile int term = 0;
    protected long commitPosition = -1L;
    protected Endpoint lastVotedFor;

    protected Endpoint leader;
    protected Member member;
    protected List<Member> members = new CopyOnWriteArrayList<>();

    protected JoinController joinController;
    protected RaftState state;
    protected Configuration configuration;

    protected final JoinResponse joinResponse = new JoinResponse();

    protected long lastContact = -1L;

    public Raft(
            final Endpoint endpoint,
            final LogStream stream,
            final int quorumHint,
            final ClientChannelManager clientChannelManager,
            final TransportConnection connection,
            final DataFramePool dataFramePool)
    {
        this(endpoint, stream, quorumHint, -1, clientChannelManager, connection, dataFramePool);
    }

    public Raft(
            final Endpoint endpoint,
            final LogStream stream,
            final int quorumHint,
            final int backupCount,
            final ClientChannelManager clientChannelManager,
            final TransportConnection connection,
            final DataFramePool dataFramePool)
    {
        this.stream = stream;

        this.context = new RaftContext()
                .clientChannelManager(clientChannelManager)
                .connection(connection)
                .dataFramePool(dataFramePool);

        final LogStreamState logStreamState = new LogStreamState(stream);

        this.leaderState = new LeaderState(this, logStreamState);
        this.candidateState = new CandidateState(this, logStreamState);
        this.followerState = new FollowerState(this, logStreamState);
        this.inactiveState = new InactiveState(this, logStreamState);

        this.state = inactiveState;

        this.joinController = new JoinController(this);
        this.member = new Member(this, endpoint, Member.Type.ACTIVE);
    }

    public int doWork()
    {
        int workcount = 0;

        workcount += joinController.doWork();
        workcount += state.doWork();

        return workcount;
    }

    public int id()
    {
        return stream.getId();
    }

    public int term()
    {
        return term;
    }

    public Raft term(final int term)
    {
        if (term > this.term)
        {
            this.term = term;
            this.leader = null;
            this.lastVotedFor = null;
        }
        return this;
    }

    public long commitPosition()
    {
        return commitPosition;
    }

    public Raft commitPosition(final long commitPosition)
    {
        final long previousCommitPosition = this.commitPosition;
        if (previousCommitPosition < commitPosition)
        {
            this.commitPosition = commitPosition;
        }
        return this;
    }

    public Endpoint leader()
    {
        return leader;
    }

    public Raft leader(final Endpoint leader)
    {
        this.leader = leader;
        return this;
    }

    public Member member()
    {
        return member;
    }

    public List<Member> members()
    {
        return members;
    }

    public LogStream stream()
    {
        return stream;
    }

    public Configuration configuration()
    {
        return configuration;
    }

    public long lastContact()
    {
        return lastContact;
    }

    public Raft lastContact(final long lastContact)
    {
        this.lastContact = lastContact;
        return this;
    }

    public RaftContext context()
    {
        return context;
    }

    public boolean isUnderstaft()
    {
        // TODO!
        return true;
    }

    public Raft bootstrap()
    {
        if (configuration == null)
        {
            final List<Member> members = new ArrayList<>();
            members.add(new Member(this, member.endpoint(), Member.Type.ACTIVE));
            configure(new Configuration(0L, 0, members));
        }

        return join();
    }

    public Raft join(final List<Member> cluster)
    {
        if (configuration == null)
        {
            final List<Member> clusterMembers = new ArrayList<>();
            for (int i = 0; i < cluster.size(); i++)
            {
                final Member member = cluster.get(i);
                if (!member.equals(this.member.endpoint()))
                {
                    clusterMembers.add(member);
                }
            }

            if (clusterMembers.isEmpty())
            {
                throw new IllegalStateException("cannot join empty cluster");
            }

            configure(new Configuration(0L, 0, clusterMembers));
        }

        return join();
    }

    protected Raft join()
    {
        final List<Member> activeMembers = new ArrayList<>();

        final int size = members.size();
        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            if (member.type() == Member.Type.ACTIVE && !member.equals(member()))
            {
                activeMembers.add(member);
            }
        }

        if (!activeMembers.isEmpty())
        {
            joinController.open(activeMembers);
        }
        else
        {

            transition(member.type());
        }

        return this;
    }

    public Raft configure(final Configuration configuration)
    {
//        if (this.configuration != null && (configuration.configurationEntryPosition() <= this.configuration.configurationEntryPosition()))
//        {
//            return this;
//        }

        boolean transition = false;
        final List<Member> configuredMembers = configuration.members();

        for (int i = 0; i < configuredMembers.size(); i++)
        {
            final Member configuredMember = configuredMembers.get(i);

            if (configuredMember.equals(member))
            {
                // TODO: promote vs. demote
                transition = member.type() != configuredMember.type();
                member.type(configuredMember.type());

                if (!members.contains(member))
                {
                    members.add(member);
                }
            }
            else
            {
                final int idx = members.indexOf(configuredMember);
                Member existingMember = idx >= 0 ? members.get(idx) : null;

                if (existingMember == null)
                {
                    existingMember = new Member(this, configuredMember.endpoint(), configuredMember.type());
                    existingMember.resetToLastEntry();
                    members.add(existingMember);
                }

                existingMember.type(configuredMember.type());
            }
        }

        if (transition)
        {
            transition(member.type());
        }

        int i = 0;
        while (i < members.size())
        {
            final Member member = members.get(i);

            if (!configuredMembers.contains(member))
            {
                try
                {
                    member.cancelVoteRequest();
                    member.cancelAppendRequest();
                }
                finally
                {
                    members.remove(member);
                }
            }
            else
            {
                i++;
            }
        }

        this.configuration = configuration;

        // TODO: if configuration is committed, then store it!

        return this;
    }

    public void transition(Member.Type type)
    {
        switch (type)
        {
            case ACTIVE:
                if (!(state instanceof ActiveState))
                {
                    transition(State.FOLLOWER);
                }
                break;
            default:
                if (this.state.state() != State.INACTIVE)
                {
                    transition(State.INACTIVE);
                }
                break;
        }
    }

    public void transition(final State state)
    {
        try
        {
            this.state.close();
        }
        finally
        {
            this.state = getState(state);
            System.out.println("transition to state: " + this.state.state());
            this.state.open();

        }
    }

    protected ActiveState getState(final State state)
    {
        switch (state)
        {
            case FOLLOWER:
                return followerState;
            case CANDIDATE:
                return candidateState;
            case LEADER:
                return leaderState;
            default:
                throw new IllegalArgumentException();
        }
    }



//    public boolean handleJoin(final JoinRequest joinRequest, DeferredResponse response)
//    {
//        return state.join(joinRequest, response);
//    }

    public void handleVote(final VoteRequest request, final VoteResponse response)
    {
//        state.vote(request, response);
    }

    public AppendResponse handleAppendRequest(final AppendRequest request)
    {
        return state.append(request);
    }

    int resets = 0;
    public void handleAppendResponse(final AppendResponse response)
    {
        if (response.succeeded())
        {
            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                if (member.endpoint().equals(response.member()))
                {
                    member.failures(0);
                    return;
                }
            }

            // TODO: do something with commit!
        }

        else if (response.term() > term())
        {
            term(response.term());
            leader(null);
            transition(State.FOLLOWER);
        }
        else if (response.member() != null)
        {
            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                if (member.endpoint().equals(response.member()))
                {
                    resets++;
                    System.out.println("\n");
                    System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> APPEND ENTRIES RESPONSE (" + resets + ") <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                    System.out.println("logPosition: " + response.entryPosition());
                    member.incrementFailures();
                    member.resetToPreviousEntry(response.entryPosition());
                    return;
                }
            }
        }
    }

    public Endpoint lastVotedFor()
    {
        return lastVotedFor;
    }

    public Raft lastVotedFor(final Endpoint lastVotedFor)
    {
        this.lastVotedFor = lastVotedFor;
        return this;
    }

    public boolean isLeader()
    {
        return state.state() == State.LEADER;
    }

    protected Member findMemberByEndpoint(final Endpoint endpoint)
    {
        final int size = members.size();
        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            if (member.endpoint().equals(endpoint))
            {
                return member;
            }
        }
        return null;
    }

    public static Builder builder(final Endpoint endpoint)
    {
        return new Builder(endpoint);
    }

    public static class Builder implements org.camunda.tngp.broker.clustering.raft.util.Builder<Raft>
    {
        protected int backupCount = 0;
        protected int quorumHint = 3;
        protected Endpoint endpoint;
        protected LogStream stream;
        protected ClientChannelManager clientChannelManager;
        protected TransportConnection connection;
        protected DataFramePool dataFramePool;

        Builder(final Endpoint endpoint)
        {
            this.endpoint = endpoint;
        }

        public Builder withBackupCount(final int backupCount)
        {
            this.backupCount = backupCount;
            return this;
        }

        public Builder withQuorumHint(final int quorumHint)
        {
            this.quorumHint = quorumHint;
            return this;
        }

        public Builder withStream(final LogStream stream)
        {
            this.stream = stream;
            return this;
        }

        public Builder withClientChannelManager(final ClientChannelManager clientChannelManager)
        {
            this.clientChannelManager = clientChannelManager;
            return this;
        }

        public Builder withConnection(final TransportConnection connection)
        {
            this.connection = connection;
            return this;
        }

        public Builder withDataFramePool(final DataFramePool dataFramePool)
        {
            this.dataFramePool = dataFramePool;
            return this;
        }

        @Override
        public Raft build()
        {
            return new Raft(
                    endpoint,
                    stream,
                    quorumHint,
                    backupCount,
                    clientChannelManager,
                    connection,
                    dataFramePool);
        }
    }

    public enum State
    {
        INACTIVE,
        FOLLOWER,
        CANDIDATE,
        LEADER
    }

}
