package io.zeebe.broker.clustering.raft;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.zeebe.broker.clustering.raft.controller.ConfigureController;
import io.zeebe.broker.clustering.raft.controller.JoinController;
import io.zeebe.broker.clustering.raft.controller.LeaveController;
import io.zeebe.broker.clustering.raft.controller.PollController;
import io.zeebe.broker.clustering.raft.controller.ReplicationController;
import io.zeebe.broker.clustering.raft.controller.VoteController;
import io.zeebe.broker.clustering.raft.handler.RaftMessageHandler;
import io.zeebe.broker.clustering.raft.message.AppendRequest;
import io.zeebe.broker.clustering.raft.message.AppendResponse;
import io.zeebe.broker.clustering.raft.message.ConfigureRequest;
import io.zeebe.broker.clustering.raft.message.ConfigureResponse;
import io.zeebe.broker.clustering.raft.message.JoinRequest;
import io.zeebe.broker.clustering.raft.message.JoinResponse;
import io.zeebe.broker.clustering.raft.message.LeaveRequest;
import io.zeebe.broker.clustering.raft.message.LeaveResponse;
import io.zeebe.broker.clustering.raft.message.PollRequest;
import io.zeebe.broker.clustering.raft.message.PollResponse;
import io.zeebe.broker.clustering.raft.message.VoteRequest;
import io.zeebe.broker.clustering.raft.message.VoteResponse;
import io.zeebe.broker.clustering.raft.state.ActiveState;
import io.zeebe.broker.clustering.raft.state.CandidateState;
import io.zeebe.broker.clustering.raft.state.FollowerState;
import io.zeebe.broker.clustering.raft.state.InactiveState;
import io.zeebe.broker.clustering.raft.state.LeaderState;
import io.zeebe.broker.clustering.raft.state.LogStreamState;
import io.zeebe.broker.clustering.raft.state.RaftState;
import io.zeebe.clustering.gossip.RaftMembershipState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.transport.ServerInputSubscription;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.WaitState;

public class Raft implements Actor
{
    private static final int TRANSITION_OPEN = 0;
    private static final int TRANSITION_DEFAULT = 1;
    private static final int TRANSITION_CLOSE = 2;

    private static final StateMachineCommand<TransitionContext> CLOSE_MACHINE_COMMAND = (c) ->
    {
        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final RaftContext context;
    private final LogStream stream;
    private final MetaStore meta;

    private final InactiveState inactiveState;
    private final FollowerState followerState;
    private final CandidateState candidateState;
    private final LeaderState leaderState;

    private boolean lastVotedForAvailable;
    private final SocketAddress lastVotedFor;

    private boolean leaderAvailable;
    private final SocketAddress leader;

    protected long lastContact;

    private final Member member;
    private List<Member> members;

    private volatile RaftState state;
    private Configuration configuration;

    private final JoinController joinController;
    private final LeaveController leaveController;

    private final ServerInputSubscription inputSubscription;

    private final List<Consumer<Raft>> stateChangeListeners;

    private final WaitState<TransitionContext> closedState = (c) ->
    {
    };
    private final OpeningState openingState = new OpeningState();
    private final OpenState openState = new OpenState();
    private final CloseState closeState = new CloseState();
    private final ClosingState closingState = new ClosingState();
    private final TransitionState transitionState = new TransitionState();

    private StateMachineAgent<TransitionContext> transitionStateMachine;
    private TransitionContext transitionContext;

    public Raft(final RaftContext context, final LogStream stream, final MetaStore meta)
    {
        this.context = context;
        this.stream = stream;
        this.meta = meta;

        final LogStreamState logStreamState = new LogStreamState(stream);

        context.setRaft(this);
        context.setLogStreamState(logStreamState);

        this.lastVotedForAvailable = false;
        this.lastVotedFor = new SocketAddress();

        this.leaderAvailable = false;
        this.leader = new SocketAddress();

        this.member = new Member(context.getRaftEndpoint(), context);
        this.members = new CopyOnWriteArrayList<>();

        this.inactiveState = new InactiveState(context);
        this.followerState = new FollowerState(context);
        this.candidateState = new CandidateState(context);
        this.leaderState = new LeaderState(context);

        this.state = inactiveState;

        final RaftMessageHandler handler = new RaftMessageHandler(context);

        // TODO: we have n rafts; name must be unique per raft
        // TODO: must have another subscription on receive buffer of client transport
        //   => cannot be the same receive buffer due to conflicting stream IDs
        //   => maybe that is a reason why a single transport concept (for both client and n servers) should be used, where stream ids are unique
        inputSubscription = context.getServerTransport().openSubscription("raft-foo", handler, handler).join();

        this.joinController = new JoinController(context);
        this.leaveController = new LeaveController(context);
        this.stateChangeListeners = new CopyOnWriteArrayList<>();

        this.stream.setTerm(meta.loadTerm());

        final SocketAddress vote = meta.loadVote();
        if (vote != null)
        {
            lastVotedForAvailable = true;
            lastVotedFor.wrap(vote);
        }

        this.configuration = meta.loadConfiguration();

        if (configuration != null)
        {
            final List<Member> members = configuration.members();
            for (int i = 0; i < members.size(); i++)
            {
                final Member m = members.get(i);
                this.members.add(new Member(m.endpoint(), context));
            }
        }

        initStateMachine();
    }

    private void initStateMachine()
    {
        transitionStateMachine = new StateMachineAgent<>(StateMachine
                .<TransitionContext> builder(s ->
                {
                    transitionContext = new TransitionContext(s, this);
                    return transitionContext;
                })
                .initialState(closedState)

                .from(closedState).take(TRANSITION_OPEN).to(openingState)
                .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                .from(openingState).take(TRANSITION_DEFAULT).to(openState)
                .from(openingState).take(TRANSITION_CLOSE).to(closeState)

                .from(openState).take(TRANSITION_CLOSE).to(closeState)

                .from(closeState).take(TRANSITION_DEFAULT).to(closingState)
                .from(closeState).take(TRANSITION_CLOSE).to(closeState)

                .from(closingState).take(TRANSITION_DEFAULT).to(transitionState)
                .from(closingState).take(TRANSITION_CLOSE).to(closingState)

                .from(transitionState).take(TRANSITION_OPEN).to(openingState)
                .from(transitionState).take(TRANSITION_DEFAULT).to(closedState)
                .from(transitionState).take(TRANSITION_CLOSE).to(transitionState)

                .build());

        transitionStateMachine.addCommand((c) ->
        {
            final boolean opened = c.tryTake(TRANSITION_OPEN);
            if (!opened)
            {
                throw new IllegalStateException();
            }
        });
    }

    @Override
    public String name()
    {
        return String.format("raft.%s", stream.getLogName());
    }

    @Override
    public int getPriority(long now)
    {
        return PRIORITY_LOW;
    }

    @Override
    public int doWork() throws Exception
    {
        int workcount = 0;

        workcount += inputSubscription.poll();
        workcount += transitionStateMachine.doWork();
        workcount += joinController.doWork();
        workcount += leaveController.doWork();

        return workcount;
    }

    public CompletableFuture<Void> closeAsync()
    {
        final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

        transitionContext.next = null;
        transitionContext.closeFuture = closeFuture;
        transitionStateMachine.addCommand(CLOSE_MACHINE_COMMAND);

        return closeFuture;
    }

    public LogStream stream()
    {
        return stream;
    }

    public MetaStore meta()
    {
        return meta;
    }

    public Configuration configuration()
    {
        return configuration;
    }

    public RaftMembershipState state()
    {
        return state.state();
    }

    public boolean needsMembers()
    {
        return isLeader(); // TODO: && not enough members;
    }

    public boolean isLeader()
    {
        return state.state() == RaftMembershipState.LEADER;
    }

    public int term()
    {
        return stream.getTerm();
    }

    public Raft term(final int term)
    {
        if (term > term())
        {
            stream.setTerm(term);
            leader(null);
            lastVotedFor(null);
            meta.storeTermAndVote(term, null);
        }
        return this;
    }

    public long commitPosition()
    {
        return stream.getCommitPosition();
    }

    public Raft commitPosition(final long commitPosition)
    {
        final long previousCommitPosition = commitPosition();
        if (previousCommitPosition < commitPosition)
        {
            stream.setCommitPosition(commitPosition);
            final long configurationPosition = configuration().configurationEntryPosition();
            if (configurationPosition > previousCommitPosition && configurationPosition <= commitPosition)
            {
                meta.storeConfiguration(configuration);
            }
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

    public Raft lastContactNow()
    {
        return lastContact(System.currentTimeMillis());
    }

    public SocketAddress leader()
    {
        return leaderAvailable ? leader : null;
    }

    public Raft leader(final SocketAddress leader)
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

    public SocketAddress lastVotedFor()
    {
        return lastVotedForAvailable ? lastVotedFor : null;
    }

    public Raft lastVotedFor(final SocketAddress lastVotedFor)
    {
        lastVotedForAvailable = false;
        this.lastVotedFor.reset();

        if (lastVotedFor != null)
        {
            lastVotedForAvailable = true;
            this.lastVotedFor.wrap(lastVotedFor);
            meta.storeVote(lastVotedFor);
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
        return (int) Math.floor(members().size() / 2.0) + 1;
    }

    public Member getMemberByEndpoint(final SocketAddress endpoint)
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

    public Raft join(final List<Member> members)
    {
        if (configuration == null)
        {
            final List<Member> configuredMembers = new CopyOnWriteArrayList<>();
            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                if (!member.equals(this.member.endpoint()))
                {
                    configuredMembers.add(member);
                }
            }

            if (configuredMembers.isEmpty())
            {
                throw new IllegalStateException("cannot join empty cluster");
            }

            configure(new Configuration(0L, 0, configuredMembers));
        }

        return join();
    }

    public CompletableFuture<Void> leave()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        leaveController.open(future);
        return future;
    }

    protected Raft join()
    {
        transition(RaftMembershipState.FOLLOWER);

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

            if (!members.contains(configuredMember))
            {
                if (member.equals(configuredMember))
                {
                    members.add(member);
                }
                else
                {
                    members.add(new Member(configuredMember.endpoint(), context));
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

                    final PollController pollController = member.getPollController();
                    pollController.closeForcibly();

                    final ConfigureController configureController = member.getConfigureController();
                    configureController.closeForcibly();
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

        if (commitPosition() >= configuration.configurationEntryPosition())
        {
            meta.storeConfiguration(this.configuration);
        }

        return this;
    }

    public void transition(final RaftMembershipState state)
    {
        if (this.state.state() != state && transitionContext.closeFuture == null)
        {
            transitionContext.next = state;
            transitionStateMachine.addCommand(CLOSE_MACHINE_COMMAND);
        }
    }

    protected ActiveState getState(final RaftMembershipState state)
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

    public AppendResponse handleAppendRequest(final AppendRequest appendRequest)
    {
        return state.append(appendRequest);
    }

    public void handleAppendResponse(final AppendResponse appendResponse)
    {
        state.appended(appendResponse);
    }

    public PollResponse handlePollRequest(final PollRequest pollRequest)
    {
        return state.poll(pollRequest);
    }

    public VoteResponse handleVoteRequest(final VoteRequest voteRequest)
    {
        return state.vote(voteRequest);
    }

    public ConfigureResponse handleConfigureRequest(final ConfigureRequest configureRequest)
    {
        return state.configure(configureRequest);
    }

    public CompletableFuture<JoinResponse> handleJoinRequest(final JoinRequest joinRequest)
    {
        return state.join(joinRequest);
    }

    public CompletableFuture<LeaveResponse> handleLeaveRequest(final LeaveRequest leaveRequest)
    {
        return state.leave(leaveRequest);
    }

    public void onStateChange(Consumer<Raft> listener)
    {
        stateChangeListeners.add(listener);
    }

    @Override
    public String toString()
    {
        return "Raft{" +
            "stream=" + stream +
            ", lastVotedFor=" + lastVotedFor +
            ", leader=" + leader +
            ", lastContact=" + lastContact +
            ", state=" + state +
            ", configuration=" + configuration +
            '}';
    }

    static class TransitionContext extends SimpleStateMachineContext
    {
        private final Raft raft;

        RaftMembershipState next;
        CompletableFuture<Void> closeFuture;

        TransitionContext(StateMachine<?> stateMachine, Raft raft)
        {
            super(stateMachine);
            this.raft = raft;
        }
    }

    static class OpeningState implements io.zeebe.util.state.State<TransitionContext>
    {
        @Override
        public int doWork(TransitionContext context) throws Exception
        {
            int workcount = 0;

            final Raft raft = context.raft;
            raft.state.open();

            workcount += 1;
            context.take(TRANSITION_DEFAULT);

            return workcount;
        }
    }

    static class OpenState implements io.zeebe.util.state.State<TransitionContext>
    {
        @Override
        public int doWork(TransitionContext context) throws Exception
        {
            final Raft raft = context.raft;
            return raft.state.doWork();
        }
    }

    static class CloseState implements io.zeebe.util.state.State<TransitionContext>
    {
        @Override
        public int doWork(TransitionContext context) throws Exception
        {
            int workcount = 0;

            final Raft raft = context.raft;
            raft.state.close();
            workcount += 1;

            context.take(TRANSITION_DEFAULT);

            return workcount;
        }
    }

    static class ClosingState implements io.zeebe.util.state.State<TransitionContext>
    {
        @Override
        public int doWork(TransitionContext context) throws Exception
        {
            int workcount = 0;

            final Raft raft = context.raft;
            final RaftState currentState = raft.state;

            workcount += currentState.doWork();

            if (currentState.isClosed())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class TransitionState implements State<TransitionContext>
    {
        @Override
        public int doWork(TransitionContext context) throws Exception
        {
            int workcount = 0;

            final Raft raft = context.raft;
            int transition = TRANSITION_DEFAULT;

            if (context.next != null)
            {
                final RaftMembershipState nextState = context.next;
                raft.state = raft.getState(nextState);

                workcount += 1;

                final List<Consumer<Raft>> listeners = raft.stateChangeListeners;
                for (int i = 0; i < listeners.size(); i++)
                {
                    workcount += 1;
                    listeners.get(i).accept(raft);
                }

                transition = TRANSITION_OPEN;
            }
            else if (context.closeFuture != null)
            {
                context.closeFuture.complete(null);
            }

            workcount += 1;
            context.take(transition);

            context.next = null;
            context.closeFuture = null;

            return workcount;
        }
    }
}
