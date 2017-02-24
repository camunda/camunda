package org.camunda.tngp.broker.clustering.raft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.raft.controller.JoinController;
import org.camunda.tngp.broker.clustering.raft.controller.ReplicationController;
import org.camunda.tngp.broker.clustering.raft.controller.VoteController;
import org.camunda.tngp.broker.clustering.raft.handler.RaftFragmentHandler;
import org.camunda.tngp.broker.clustering.raft.state.ActiveState;
import org.camunda.tngp.broker.clustering.raft.state.CandidateState;
import org.camunda.tngp.broker.clustering.raft.state.FollowerState;
import org.camunda.tngp.broker.clustering.raft.state.InactiveState;
import org.camunda.tngp.broker.clustering.raft.state.LeaderState;
import org.camunda.tngp.broker.clustering.raft.state.LogStreamState;
import org.camunda.tngp.broker.clustering.raft.state.RaftState;
import org.camunda.tngp.logstreams.log.LogStream;

public class Raft implements Agent
{
    private final RaftContext context;
    private final LogStream stream;

    private final InactiveState inactiveState;
    private final FollowerState followerState;
    private final CandidateState candidateState;
    private final LeaderState leaderState;

    private volatile int term = 0;

    private boolean lastVotedForAvailable;
    private final Endpoint lastVotedFor;

    private boolean leaderAvailable;
    private final Endpoint leader;

    private long commitPosition = -1L;

    protected long lastContact;

    private final Member member;
    private List<Member> members;

    private volatile RaftState state;
    private Configuration configuration;

    private final JoinController joinController;

    private final RaftFragmentHandler fragmentHandler;

    public Raft(final RaftContext context, final LogStream stream)
    {
        this.context = context;
        this.stream = stream;

        final LogStreamState logStreamState = new LogStreamState(stream);

        context.setRaft(this);
        context.setLogStreamState(logStreamState);

        this.lastVotedForAvailable = false;
        this.lastVotedFor = new Endpoint();

        this.leaderAvailable = false;
        this.leader = new Endpoint();

        this.member = new Member(context.getRaftEndpoint(), context);
        this.members = new CopyOnWriteArrayList<>();

        this.inactiveState = new InactiveState(context);
        this.followerState = new FollowerState(context);
        this.candidateState = new CandidateState(context);
        this.leaderState = new LeaderState(context);

        this.state = inactiveState;

        this.fragmentHandler = new RaftFragmentHandler(this, context.getSubscription());
        this.joinController = new JoinController(context);
    }

    @Override
    public String roleName()
    {
        return String.format("raft.%d", stream.getId());
    }

    @Override
    public int doWork() throws Exception
    {
        int workcount = 0;

        workcount += fragmentHandler.doWork();
        workcount += state.doWork();
        workcount += joinController.doWork();

        return workcount;
    }

    public int id()
    {
        return stream.getId();
    }

    public LogStream stream()
    {
        return stream;
    }

    public Configuration configuration()
    {
        return configuration;
    }

    public State state()
    {
        return state.state();
    }

    public boolean needMembers()
    {
        return isLeader(); // TODO: && not enough members;
    }

    public boolean isLeader()
    {
        return state.state() == State.LEADER;
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
            leader(null);
            lastVotedFor(null);
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

    public long lastContact()
    {
        return lastContact;
    }

    public Raft lastContact(final long lastContact)
    {
        this.lastContact = lastContact;
        return this;
    }

    public Endpoint leader()
    {
        return leaderAvailable ? leader : null;
    }

    public Raft leader(final Endpoint leader)
    {
        leaderAvailable = false;
        this.leader.reset();

        if (leader != null)
        {
            leaderAvailable = true;
            this.leader.wrap(leader);
        }

        return this;
    }

    public Endpoint lastVotedFor()
    {
        return lastVotedForAvailable ? lastVotedFor : null;
    }

    public Raft lastVotedFor(final Endpoint lastVotedFor)
    {
        lastVotedForAvailable = false;
        this.lastVotedFor.reset();

        if (lastVotedFor != null)
        {
            lastVotedForAvailable = true;
            this.lastVotedFor.wrap(lastVotedFor);
        }

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

    public int quorum()
    {
        return (int) Math.floor((members().size() + 1) / 2.0) + 1;
    }

    public Member getMemberByEndpoint(final Endpoint endpoint)
    {
        for (int i = 0; i < members.size(); i++)
        {
            final Member member = members.get(i);
            if (member.endpoint().equals(endpoint))
            {
                return member;
            }
        }

        return null;
    }

    public Raft bootstrap()
    {
        if (configuration == null)
        {
            final List<Member> members = new CopyOnWriteArrayList<>();
            members.add(new Member(member.endpoint(), context));
            configure(new Configuration(0L, 0, members));
        }

        return join();
    }

    public Raft join(final List<Member> cluster)
    {
        if (configuration == null)
        {
            final List<Member> clusterMembers = new CopyOnWriteArrayList<>();
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
        final List<Member> activeMembers = new CopyOnWriteArrayList<>();

        final int size = members.size();
        for (int i = 0; i < size; i++)
        {
            final Member member = members.get(i);
            if (!member.equals(member()))
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
            transition(State.FOLLOWER);
        }

        return this;
    }

    public Raft configure(final Configuration configuration)
    {
        if (this.configuration != null && (configuration.configurationEntryPosition() <= this.configuration.configurationEntryPosition()))
        {
            return this;
        }

        final List<Member> configuredMembers = configuration.members();

        for (int i = 0; i < configuredMembers.size(); i++)
        {
            final Member configuredMember = configuredMembers.get(i);

            if (configuredMember.equals(member) && !members.contains(member))
            {
                members.add(member);
            }
            else
            {
                final int idx = members.indexOf(configuredMember);
                Member existingMember = idx >= 0 ? members.get(idx) : null;

                if (existingMember == null)
                {
                    existingMember = new Member(configuredMember.endpoint(), context);
                    members.add(existingMember);
                }
            }
        }

        int i = 0;
        while (i < members.size())
        {
            final Member member = members.get(i);

            if (!configuredMembers.contains(member))
            {
                try
                {
                    final VoteController voteController = member.getVoteController();
                    voteController.closeForcibly();

                    final ReplicationController replicationController = member.getReplicationController();
                    replicationController.closeForcibly();
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

    public void transition(final State state)
    {
        if (this.state.state() != state)
        {
            try
            {
                this.state.close();

                while (!this.state.isClosed())
                {
                    this.state.doWork();
                }
            }
            finally
            {
                this.state = getState(state);
                this.state.open();
            }
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

    public int onVoteRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connection, final long requestId)
    {
        return state.onVoteRequest(buffer, offset, length, channelId, connection, requestId);
    }

    public int onAppendRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId)
    {
        return state.onAppendRequest(buffer, offset, length, channelId);
    }

    public int onAppendResponse(final DirectBuffer buffer, final int offset, final int length)
    {
        return state.onAppendResponse(buffer, offset, length);
    }

    public int onJoinRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connectionId, final long requestId)
    {
        return state.onJoinRequest(buffer, offset, length, channelId, connectionId, requestId);
    }

    public int onConfigureRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connectionId, final long requestId)
    {
        return state.onConfigureRequest(buffer, offset, length, channelId, connectionId, requestId);
    }

    public enum State
    {
        INACTIVE, FOLLOWER, CANDIDATE, LEADER
    }

}
