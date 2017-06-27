package io.zeebe.broker.clustering.raft.state;

import static io.zeebe.broker.logstreams.LogStreamServiceNames.LOG_STREAM_SERVICE_GROUP;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.logStreamServiceName;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.clustering.gossip.RaftMembershipState.FOLLOWER;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import io.zeebe.broker.clustering.raft.Configuration;
import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.broker.clustering.raft.controller.AppendController;
import io.zeebe.broker.clustering.raft.controller.ConfigurationController;
import io.zeebe.broker.clustering.raft.controller.ConfigureController;
import io.zeebe.broker.clustering.raft.controller.ReplicationController;
import io.zeebe.broker.clustering.raft.entry.InitializeEntry;
import io.zeebe.broker.clustering.raft.message.AppendRequest;
import io.zeebe.broker.clustering.raft.message.AppendResponse;
import io.zeebe.broker.clustering.raft.message.JoinRequest;
import io.zeebe.broker.clustering.raft.message.JoinResponse;
import io.zeebe.broker.clustering.raft.message.LeaveRequest;
import io.zeebe.broker.clustering.raft.message.LeaveResponse;
import io.zeebe.broker.clustering.raft.message.PollRequest;
import io.zeebe.broker.clustering.raft.message.PollResponse;
import io.zeebe.broker.clustering.raft.message.VoteRequest;
import io.zeebe.broker.clustering.raft.message.VoteResponse;
import io.zeebe.broker.logstreams.LogStreamService;
import io.zeebe.clustering.gossip.RaftMembershipState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

public class LeaderState extends ActiveState
{
    private static final int TRANSITION_OPEN = 0;
    private static final int TRANSITION_DEFAULT = 1;
    private static final int TRANSITION_CLOSE = 2;

    private static final StateMachineCommand<LeaderContext> CLOSE_LEADERSHIP_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private static final StateMachineCommand<ReplicationContext> CLOSE_REPLICATION_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private static final StateMachineCommand<ConfigureContext> CLOSE_CONFIGURE_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final WaitState<LeaderContext> leaderClosedState = (c) ->
    {
    };
    private final WaitState<LeaderContext> leaderInitializedState = (c) ->
    {
    };

    private final StartLogStreamControllerState startLogStreamControllerState = new StartLogStreamControllerState();
    private final OpeningLogStreamControllerState openingLogStreamControllerState = new OpeningLogStreamControllerState();
    private final OpenAppendControllerState openAppendControllerState = new OpenAppendControllerState();
    private final AppendInitialEntryState appendInitialEntryState = new AppendInitialEntryState();
    private final OpenConfigurationControllerState openConfigurationControllerState = new OpenConfigurationControllerState();
    private final AppendConfigurationEntryState appendConfigurationEntryState = new AppendConfigurationEntryState();
    private final InstallLogStreamServiceState installLogStreamServiceState = new InstallLogStreamServiceState();
    private final RemoveLogStreamServiceState removeLogStreamServiceState = new RemoveLogStreamServiceState();
    private final RemovingLogStreamServiceState removingLogStreamServiceState = new RemovingLogStreamServiceState();
    private final StopLogStreamControllerState stopLogStreamControllerState = new StopLogStreamControllerState();
    private final CloseAppendControllerState closeAppendControllerState = new CloseAppendControllerState();
    private final ClosingAppendControllerState closingAppendControllerState = new ClosingAppendControllerState();

    private final StateMachineAgent<LeaderContext> leadershipStateMachine;
    private LeaderContext leaderContext;

    private final WaitState<ReplicationContext> replicationClosedState = (c) ->
    {
    };

    private final OpenReplicationState openReplicationState = new OpenReplicationState();
    private final CloseReplicationsState closeReplicationsState = new CloseReplicationsState();
    private final ClosingReplicationsState closingReplicationsState = new ClosingReplicationsState();

    private final StateMachineAgent<ReplicationContext> replicationStateMachine;
    private ReplicationContext replicationContext;

    private final WaitState<ConfigureContext> configureClosedState = (c) ->
    {
    };

    private final OpenConfigureState openConfigureState = new OpenConfigureState();
    private final CloseConfigureState closeConfigureState = new CloseConfigureState();
    private final ClosingConfigureState closingConfigureState = new ClosingConfigureState();

    private final StateMachineAgent<ConfigureContext> configureStateMachine;
    private ConfigureContext configureContext;

    private final List<Member> configuringMembers;
    private final Member configuringMember;

    private final ConfigurationController configurationController;

    private long leaderPosition;

    public LeaderState(final RaftContext context)
    {
        super(context);

        this.leaderPosition = -1L;

        this.configuringMembers = new CopyOnWriteArrayList<>();
        this.configuringMember = new Member();

        this.configurationController = new ConfigurationController(context);

        this.leadershipStateMachine = new StateMachineAgent<>(StateMachine
                .<LeaderContext> builder(s ->
                {
                    leaderContext = new LeaderContext(s, context);
                    leaderContext.configController = configurationController;
                    return leaderContext;
                })
                .initialState(leaderClosedState)

                .from(leaderClosedState).take(TRANSITION_OPEN).to(startLogStreamControllerState)
                .from(leaderClosedState).take(TRANSITION_CLOSE).to(leaderClosedState)

                .from(startLogStreamControllerState).take(TRANSITION_DEFAULT).to(openingLogStreamControllerState)
                .from(startLogStreamControllerState).take(TRANSITION_CLOSE).to(removeLogStreamServiceState)

                .from(openingLogStreamControllerState).take(TRANSITION_DEFAULT).to(openAppendControllerState)
                .from(openingLogStreamControllerState).take(TRANSITION_CLOSE).to(removeLogStreamServiceState)

                .from(openAppendControllerState).take(TRANSITION_DEFAULT).to(appendInitialEntryState)
                .from(openAppendControllerState).take(TRANSITION_CLOSE).to(removeLogStreamServiceState)

                .from(appendInitialEntryState).take(TRANSITION_DEFAULT).to(openConfigurationControllerState)
                .from(appendInitialEntryState).take(TRANSITION_CLOSE).to(removeLogStreamServiceState)

                .from(openConfigurationControllerState).take(TRANSITION_DEFAULT).to(appendConfigurationEntryState)
                .from(openConfigurationControllerState).take(TRANSITION_CLOSE).to(removeLogStreamServiceState)

                .from(appendConfigurationEntryState).take(TRANSITION_DEFAULT).to(installLogStreamServiceState)
                .from(appendConfigurationEntryState).take(TRANSITION_CLOSE).to(removeLogStreamServiceState)

                .from(installLogStreamServiceState).take(TRANSITION_DEFAULT).to(leaderInitializedState)
                .from(installLogStreamServiceState).take(TRANSITION_CLOSE).to(removeLogStreamServiceState)

                .from(leaderInitializedState).take(TRANSITION_CLOSE).to(removeLogStreamServiceState)

                .from(removeLogStreamServiceState).take(TRANSITION_DEFAULT).to(removingLogStreamServiceState)
                .from(removeLogStreamServiceState).take(TRANSITION_CLOSE).to(removeLogStreamServiceState)

                .from(removingLogStreamServiceState).take(TRANSITION_DEFAULT).to(stopLogStreamControllerState)
                .from(removingLogStreamServiceState).take(TRANSITION_CLOSE).to(removingLogStreamServiceState)

                .from(stopLogStreamControllerState).take(TRANSITION_DEFAULT).to(closeAppendControllerState)
                .from(stopLogStreamControllerState).take(TRANSITION_CLOSE).to(stopLogStreamControllerState)

                .from(closeAppendControllerState).take(TRANSITION_DEFAULT).to(closingAppendControllerState)
                .from(closeAppendControllerState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

                .from(closingAppendControllerState).take(TRANSITION_DEFAULT).to(leaderClosedState)
                .from(closingAppendControllerState).take(TRANSITION_CLOSE).to(closingAppendControllerState)

                .build());

        this.replicationStateMachine = new StateMachineAgent<>(StateMachine
                .<ReplicationContext> builder(s ->
                {
                    replicationContext = new ReplicationContext(s, context);
                    return replicationContext;
                })
                .initialState(replicationClosedState)

                .from(replicationClosedState).take(TRANSITION_OPEN).to(openReplicationState)
                .from(replicationClosedState).take(TRANSITION_CLOSE).to(replicationClosedState)

                .from(openReplicationState).take(TRANSITION_CLOSE).to(closeReplicationsState)

                .from(closeReplicationsState).take(TRANSITION_DEFAULT).to(closingReplicationsState)
                .from(closeReplicationsState).take(TRANSITION_CLOSE).to(closeReplicationsState)

                .from(closingReplicationsState).take(TRANSITION_DEFAULT).to(replicationClosedState)
                .from(closingReplicationsState).take(TRANSITION_CLOSE).to(closingReplicationsState)

                .build());

        this.configureStateMachine = new StateMachineAgent<>(StateMachine
                .<ConfigureContext> builder(s ->
                {
                    configureContext = new ConfigureContext(s, context);
                    return configureContext;
                })
                .initialState(configureClosedState)

                .from(configureClosedState).take(TRANSITION_OPEN).to(openConfigureState)
                .from(configureClosedState).take(TRANSITION_CLOSE).to(configureClosedState)

                .from(openConfigureState).take(TRANSITION_CLOSE).to(closeConfigureState)

                .from(closeConfigureState).take(TRANSITION_DEFAULT).to(closingConfigureState)
                .from(closeConfigureState).take(TRANSITION_CLOSE).to(closeConfigureState)

                .from(closingConfigureState).take(TRANSITION_DEFAULT).to(configureClosedState)
                .from(closingConfigureState).take(TRANSITION_CLOSE).to(closingConfigureState)

                .build());
    }

    @Override
    public PollResponse poll(PollRequest pollRequest)
    {
        pollResponse.reset();
        return pollResponse
                .term(raft.term())
                .granted(false);
    }

    @Override
    public VoteResponse vote(VoteRequest voteRequest)
    {
        if (updateTermAndLeader(voteRequest.term(), null))
        {
            raft.transition(FOLLOWER);
            return super.vote(voteRequest);
        }

        voteResponse.reset();
        return voteResponse
                .term(raft.term())
                .granted(false);
    }

    @Override
    public AppendResponse append(AppendRequest appendRequest)
    {
        final LogStream logStream = raft.stream();
        final int currentTerm = raft.term();

        final Member leader = appendRequest.leader();
        final int appendRequestTerm = appendRequest.term();

        if (updateTermAndLeader(appendRequestTerm, leader))
        {
            raft.transition(FOLLOWER);
            return super.append(appendRequest);
        }

        if (appendRequestTerm < currentTerm)
        {
            appendResponse.reset();
            return appendResponse
                .topicName(logStream.getTopicName())
                .partitionId(logStream.getPartitionId())
                .term(currentTerm)
                .succeeded(false);
        }

        raft.leader(leader != null ? leader.endpoint() : null);
        return super.append(appendRequest);
    }

    @Override
    public void appended(final AppendResponse appendResponse)
    {
        final Member respondedBy = appendResponse.member();
        final boolean succeeded = appendResponse.succeeded();
        final long entryPosition = appendResponse.entryPosition();

        if (updateTermAndLeader(appendResponse.term(), null))
        {
            raft.transition(FOLLOWER);
        }
        else if (respondedBy != null)
        {
            final Member member = raft.getMemberByEndpoint(respondedBy.endpoint());

            if (member != null)
            {
                member.lastContact(System.currentTimeMillis());

                if (succeeded)
                {
                    member.failures(0);
                    member.matchPosition(entryPosition);

                    commitEntry(entryPosition);
                }
                else
                {
                    member.incrementFailures();
                    member.resetReaderToPreviousEntry(entryPosition);
                }
            }
        }
    }

    protected void commitEntry(final long position)
    {
        final List<Member> members = raft.members();
        final Member self = raft.member();

        if (canCommitPosition(position))
        {
            int replicas = 1;

            if (members.size() > 1)
            {
                for (int i = 0; i < members.size(); i++)
                {
                    final Member member = members.get(i);
                    final long matchPosition = member.matchPosition();

                    if (!self.equals(member) && position <= matchPosition)
                    {
                        replicas += 1;
                    }
                }
            }

            final int quorum = raft.quorum();
            if (replicas >= quorum)
            {
                raft.commitPosition(position);
            }
        }
    }

    protected boolean canCommitPosition(final long position)
    {
        final long previousCommitPosition = raft.commitPosition();
        return leaderPosition > -1 && position > -1 && position > previousCommitPosition && position >= leaderPosition;
    }

    @Override
    public CompletableFuture<JoinResponse> join(final JoinRequest joinRequest)
    {
        final int term = raft.term();

        final List<Member> members = raft.members();

        if (initializing() || configuring())
        {
            joinResponse.reset();
            return CompletableFuture.completedFuture(
                joinResponse
                    .term(term)
                    .succeeded(false)
                    .members(members)
            );
        }

        if (members.contains(joinRequest.member()))
        {
            joinResponse.reset();
            return CompletableFuture.completedFuture(
                joinResponse
                    .term(term)
                    .succeeded(true)
                    .members(members)
            );
        }

        final SocketAddress endpoint = joinRequest.member().endpoint();
        configuringMember.endpoint().wrap(endpoint);

        configuringMembers.clear();
        configuringMembers.addAll(members);
        configuringMembers.add(configuringMember);

        return configurationController.openAsync(configuringMembers)
                .thenAccept((c) ->
                {
                    final Configuration configuration = raft.configuration();
                    joinResponse.reset();
                    joinResponse
                        .term(term)
                        .succeeded(true)
                        .configurationEntryPosition(configuration.configurationEntryPosition())
                        .configurationEntryTerm(configuration.configurationEntryTerm())
                        .members(configuration.members());
                })
                .exceptionally((e) ->
                {
                    joinResponse.reset();
                    joinResponse
                        .term(term)
                        .succeeded(false)
                        .members(raft.configuration().members());
                    return null;
                })
                .<JoinResponse> thenCompose((c) ->
                {
                    return CompletableFuture.completedFuture(joinResponse);
                });
    }

    @Override
    public CompletableFuture<LeaveResponse> leave(final LeaveRequest leaveRequest)
    {
        final int term = raft.term();

        final List<Member> members = raft.members();

        if (initializing() || configuring())
        {
            leaveResponse.reset();
            return CompletableFuture.completedFuture(
                leaveResponse
                    .term(term)
                    .succeeded(false)
                    .members(members)
            );
        }

        if (!members.contains(leaveRequest.member()))
        {
            leaveResponse.reset();
            return CompletableFuture.completedFuture(
                leaveResponse
                    .term(term)
                    .succeeded(true)
                    .members(members)
            );
        }

        final SocketAddress endpoint = leaveRequest.member().endpoint();
        configuringMember.endpoint().wrap(endpoint);

        configuringMembers.clear();
        configuringMembers.addAll(members);
        configuringMembers.remove(configuringMember);

        return configurationController.openAsync(configuringMembers)
                .thenAccept((c) ->
                {
                    final Configuration configuration = raft.configuration();
                    leaveResponse.reset();
                    leaveResponse
                        .term(term)
                        .succeeded(true)
                        .configurationEntryPosition(configuration.configurationEntryPosition())
                        .configurationEntryTerm(configuration.configurationEntryTerm())
                        .members(configuration.members());
                })
                .exceptionally((e) ->
                {
                    leaveResponse.reset();
                    leaveResponse
                        .term(term)
                        .succeeded(false)
                        .members(raft.configuration().members());
                    return null;
                })
                .thenCompose((c) ->
                    CompletableFuture.completedFuture(leaveResponse));
    }

    protected boolean initializing()
    {
        return leadershipStateMachine.getCurrentState() != leaderInitializedState;
    }

    protected boolean configuring()
    {
        return !configurationController.isClosed();
    }

    @Override
    public RaftMembershipState state()
    {
        return RaftMembershipState.LEADER;
    }

    @Override
    public int doWork()
    {
        int workcount = 0;

        workcount += replicationStateMachine.doWork();
        workcount += configureStateMachine.doWork();
        workcount += leadershipStateMachine.doWork();
        workcount += configurationController.doWork();

        if (configurationController.isConfigured() || configurationController.isFailed())
        {
            workcount += 1;
            configurationController.close();
        }

        return workcount;
    }

    @Override
    public void open()
    {
        if (isLeadershipStateMachineClosed())
        {
            leaderContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }


        if (isReplicationStateMachineClosed())
        {
            replicationContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }


        if (isConfigureStateMachineClosed())
        {
            configureContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    @Override
    public void close()
    {
        replicationStateMachine.addCommand(CLOSE_REPLICATION_STATE_MACHINE_COMMAND);
        leadershipStateMachine.addCommand(CLOSE_LEADERSHIP_STATE_MACHINE_COMMAND);
        configureStateMachine.addCommand(CLOSE_CONFIGURE_STATE_MACHINE_COMMAND);
        configurationController.close();

        leaderPosition = -1L;
    }

    @Override
    public boolean isClosed()
    {
        return isLeadershipStateMachineClosed() && isReplicationStateMachineClosed() && isConfigureStateMachineClosed();
    }

    protected boolean isLeadershipStateMachineClosed()
    {
        return leadershipStateMachine.getCurrentState() == leaderClosedState;
    }

    protected boolean isReplicationStateMachineClosed()
    {
        return replicationStateMachine.getCurrentState() == replicationClosedState;
    }

    protected boolean isConfigureStateMachineClosed()
    {
        return configureStateMachine.getCurrentState() == configureClosedState;
    }

    static class LeaderContext extends SimpleStateMachineContext
    {
        final RaftContext raftContext;
        final AppendController appendController;
        final InitializeEntry initialEntry;

        ConfigurationController configController;
        CompletableFuture<Void> logStreamRemoveFuture;
        CompletableFuture<Void> logStreamControllerFuture;

        LeaderContext(final StateMachine<?> stateMachine, final RaftContext raftContext)
        {
            super(stateMachine);
            this.raftContext = raftContext;
            this.appendController = new AppendController(raftContext);
            this.initialEntry = new InitializeEntry();
        }

        @Override
        public void reset()
        {
            initialEntry.reset();
            logStreamRemoveFuture = null;
            logStreamControllerFuture = null;
        }
    }

    static class StartLogStreamControllerState implements State<LeaderContext>
    {
        @Override
        public int doWork(LeaderContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;

            final Raft raft = raftContext.getRaft();
            final ActorScheduler actorScheduler = raftContext.getTaskScheduler();

            final LogStream stream = raft.stream();

            int workcount = 0;

            workcount += 1;
            context.logStreamControllerFuture = stream.openLogStreamController(actorScheduler);

            context.take(TRANSITION_DEFAULT);

            return workcount;
        }
    }

    static class OpeningLogStreamControllerState implements State<LeaderContext>
    {
        @Override
        public int doWork(LeaderContext context) throws Exception
        {
            int workcount = 0;

            final CompletableFuture<Void> future = context.logStreamControllerFuture;

            if (future.isDone())
            {
                context.logStreamControllerFuture = null;
                future.get();

                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }

        @Override
        public void onFailure(LeaderContext context, Exception e)
        {
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();
            raft.transition(FOLLOWER);
        }
    }

    static class OpenAppendControllerState implements TransitionState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            final InitializeEntry initialEntry = context.initialEntry;
            final AppendController appendController = context.appendController;
            initialEntry.reset();

            appendController.open(initialEntry, false);
            context.take(TRANSITION_DEFAULT);
        }
    }

    class AppendInitialEntryState implements State<LeaderContext>
    {
        @Override
        public int doWork(LeaderContext context) throws Exception
        {
            final AppendController appendController = context.appendController;
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();

            int workcount = 0;

            workcount += appendController.doWork();

            if (appendController.isAppended())
            {
                leaderPosition = appendController.entryPosition();
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }
            else if (appendController.isFailed())
            {
                workcount += 1;
                raft.transition(FOLLOWER);
            }

            return workcount;
        }
    }

    static class OpenConfigurationControllerState implements TransitionState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            final ConfigurationController configController = context.configController;
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();
            final Configuration configuration = raft.configuration();
            configController.open(configuration.members());
            context.take(TRANSITION_DEFAULT);
        }
    }

    static class AppendConfigurationEntryState implements State<LeaderContext>
    {
        @Override
        public int doWork(LeaderContext context) throws Exception
        {
            int workcount = 0;

            final ConfigurationController controller = context.configController;

            if (controller.isAppended())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class InstallLogStreamServiceState implements TransitionState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;

            final Raft raft = raftContext.getRaft();
            final ServiceContainer serviceContainer = raftContext.getServiceContainer();

            final LogStream stream = raft.stream();

            final ServiceName<LogStream> serviceName = logStreamServiceName(stream.getLogName());
            final LogStreamService service = new LogStreamService(stream);
            serviceContainer.createService(serviceName, service)
                .dependency(ACTOR_SCHEDULER_SERVICE)
                .group(LOG_STREAM_SERVICE_GROUP)
                .install();

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class RemoveLogStreamServiceState implements TransitionState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();
            final LogStream stream = raft.stream();

            final ServiceContainer serviceContainer = raftContext.getServiceContainer();
            final ServiceName<LogStream> serviceName = logStreamServiceName(stream.getLogName());
            if (serviceContainer.hasService(serviceName))
            {
                context.logStreamRemoveFuture = serviceContainer.removeService(serviceName);
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class RemovingLogStreamServiceState implements State<LeaderContext>
    {
        @Override
        public int doWork(LeaderContext context) throws Exception
        {
            int workcount = 0;

            final CompletableFuture<Void> future = context.logStreamRemoveFuture;

            if (future == null || future.isDone())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class StopLogStreamControllerState implements TransitionState<LeaderContext>
    {
        CompletableFuture<Void> completableFuture;
        @Override
        public void work(LeaderContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();
            final LogStream stream = raft.stream();
            if (completableFuture == null)
            {
                completableFuture = stream.closeLogStreamController();
            }
            if (completableFuture.isDone())
            {
                completableFuture = null;
                context.take(TRANSITION_DEFAULT);
            }
        }
    }

    static class CloseAppendControllerState implements TransitionState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            final AppendController appendController = context.appendController;

            if (!appendController.isClosed())
            {
                appendController.close();
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ClosingAppendControllerState implements State<LeaderContext>
    {
        @Override
        public int doWork(LeaderContext context) throws Exception
        {
            final AppendController appendController = context.appendController;

            int workcount = 0;

            workcount += appendController.doWork();

            if (appendController.isClosed())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class ReplicationContext extends SimpleStateMachineContext
    {
        final RaftContext raftContext;

        ReplicationContext(StateMachine<?> stateMachine, final RaftContext raftContext)
        {
            super(stateMachine);
            this.raftContext = raftContext;
        }
    }

    class OpenReplicationState implements State<ReplicationContext>
    {

        @Override
        public int doWork(ReplicationContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();
            final LogStream stream = raft.stream();

            final List<Member> members = raft.members();
            final Member self = raft.member();
            final int size = members.size();

            int workcount = 0;

            if (size > 1)
            {
                for (int i = 0; i < members.size(); i++)
                {
                    final Member member = members.get(i);
                    final ReplicationController replicationController = member.getReplicationController();

                    if (!self.equals(member) && replicationController != null)
                    {
                        if (replicationController.isClosed())
                        {
                            member.resetReaderToLastEntry();
                            replicationController.open();
                            workcount += 1;
                        }

                        workcount += replicationController.doWork();
                    }
                }
            }
            else
            {
                final long appenderPosition = stream.getCurrentAppenderPosition();
                if (canCommitPosition(appenderPosition))
                {
                    raft.commitPosition(appenderPosition);
                    workcount += 1;
                }
            }

            return workcount;
        }
    }

    static class CloseReplicationsState implements TransitionState<ReplicationContext>
    {
        @Override
        public void work(ReplicationContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();

            final List<Member> members = raft.members();
            final Member self = raft.member();

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final ReplicationController replicationController = member.getReplicationController();

                if (!self.equals(member) && replicationController != null)
                {
                    if (!replicationController.isClosed())
                    {
                        replicationController.close();
                    }
                }
            }
            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ClosingReplicationsState implements State<ReplicationContext>
    {
        @Override
        public int doWork(ReplicationContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();

            final List<Member> members = raft.members();
            final Member self = raft.member();

            int workcount = 0;
            int closed = 0;

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final ReplicationController replicationController = member.getReplicationController();

                if (!self.equals(member) && replicationController != null)
                {
                    workcount += replicationController.doWork();

                    if (replicationController.isClosed())
                    {
                        closed += 1;
                    }
                }
            }

            if (closed == members.size() - 1)
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class ConfigureContext extends SimpleStateMachineContext
    {
        final RaftContext raftContext;

        ConfigureContext(StateMachine<?> stateMachine, final RaftContext raftContext)
        {
            super(stateMachine);
            this.raftContext = raftContext;
        }
    }

    static class OpenConfigureState implements State<ConfigureContext>
    {

        @Override
        public int doWork(ConfigureContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();

            final List<Member> members = raft.members();
            final Member self = raft.member();

            int workcount = 0;

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final ConfigureController configureController = member.getConfigureController();

                if (!self.equals(member) && configureController != null)
                {
                    if (configureController.isClosed())
                    {
                        configureController.open();
                        workcount += 1;
                    }

                    workcount += configureController.doWork();
                }
            }

            return workcount;
        }
    }

    static class CloseConfigureState implements TransitionState<ConfigureContext>
    {
        @Override
        public void work(ConfigureContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();

            final List<Member> members = raft.members();
            final Member self = raft.member();

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final ConfigureController configureController = member.getConfigureController();

                if (!self.equals(member) && configureController != null)
                {
                    if (!configureController.isClosed())
                    {
                        configureController.close();
                    }
                }
            }
            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ClosingConfigureState implements State<ConfigureContext>
    {
        @Override
        public int doWork(ConfigureContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();

            final List<Member> members = raft.members();
            final Member self = raft.member();

            int workcount = 0;
            int closed = 0;

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final ConfigureController configureController = member.getConfigureController();

                if (!self.equals(member) && configureController != null)
                {
                    workcount += configureController.doWork();

                    if (configureController.isClosed())
                    {
                        closed += 1;
                    }
                }
            }

            if (closed == members.size() - 1)
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }
}
