package org.camunda.tngp.broker.clustering.raft;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.agrona.concurrent.Agent;
import org.camunda.tngp.broker.clustering.raft.controller.ConfigureController;
import org.camunda.tngp.broker.clustering.raft.controller.JoinController;
import org.camunda.tngp.broker.clustering.raft.controller.LeaveController;
import org.camunda.tngp.broker.clustering.raft.controller.PollController;
import org.camunda.tngp.broker.clustering.raft.controller.ReplicationController;
import org.camunda.tngp.broker.clustering.raft.controller.VoteController;
import org.camunda.tngp.broker.clustering.raft.handler.RaftMessageHandler;
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
import org.camunda.tngp.broker.clustering.raft.state.ActiveState;
import org.camunda.tngp.broker.clustering.raft.state.CandidateState;
import org.camunda.tngp.broker.clustering.raft.state.FollowerState;
import org.camunda.tngp.broker.clustering.raft.state.InactiveState;
import org.camunda.tngp.broker.clustering.raft.state.LeaderState;
import org.camunda.tngp.broker.clustering.raft.state.LogStreamState;
import org.camunda.tngp.broker.clustering.raft.state.RaftState;
import org.camunda.tngp.clustering.gossip.RaftMembershipState;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.StateMachineCommand;
import org.camunda.tngp.util.state.WaitState;

public class Raft implements Agent
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

    private final RaftMessageHandler fragmentHandler;

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

        this.fragmentHandler = new RaftMessageHandler(context, context.getSubscription());
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
    public String roleName()
    {
        return String.format("raft.%s", stream.getLogName());
    }

    @Override
    public int doWork() throws Exception
    {
        int workcount = 0;

        workcount += fragmentHandler.doWork();
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

    public boolean needMembers()
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

    static class OpeningState implements org.camunda.tngp.util.state.State<TransitionContext>
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

    static class OpenState implements org.camunda.tngp.util.state.State<TransitionContext>
    {
        @Override
        public int doWork(TransitionContext context) throws Exception
        {
            final Raft raft = context.raft;
            return raft.state.doWork();
        }
    }

    static class CloseState implements org.camunda.tngp.util.state.State<TransitionContext>
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

    static class ClosingState implements org.camunda.tngp.util.state.State<TransitionContext>
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
