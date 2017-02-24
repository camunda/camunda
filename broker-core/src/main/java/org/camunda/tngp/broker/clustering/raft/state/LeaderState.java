package org.camunda.tngp.broker.clustering.raft.state;

import static org.camunda.tngp.broker.clustering.raft.Raft.State.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.raft.Configuration;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.clustering.raft.controller.AppendController;
import org.camunda.tngp.broker.clustering.raft.controller.ConfigurationController;
import org.camunda.tngp.broker.clustering.raft.controller.ReplicationController;
import org.camunda.tngp.broker.clustering.raft.entry.InitializeEntry;
import org.camunda.tngp.broker.clustering.util.MessageWriter;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.StateMachineCommand;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class LeaderState extends ActiveState
{
    private static final int TRANSITION_OPEN = 0;
    private static final int TRANSITION_DEFAULT = 1;
    private static final int TRANSITION_CLOSE = 2;

    private static final StateMachineCommand<LeaderContext> CLOSE_LEADER_STATE_MACHINE_COMMAND = (c) ->
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

    private final WaitState<LeaderContext> leaderClosedState = (c) ->
    {
    };
    private final WaitState<LeaderContext> leaderInitializedState = (c) ->
    {
    };

    private final OpenAppendControllerState openAppendControllerState = new OpenAppendControllerState();
    private final AppendInitialEntryState appendInitialEntryState = new AppendInitialEntryState();
    private final OpenConfigurationControllerState openConfigurationControllerState = new OpenConfigurationControllerState();
    private final CloseAppendControllerState closeAppendControllerState = new CloseAppendControllerState();
    private final ClosingAppendControllerState closingAppendControllerState = new ClosingAppendControllerState();

    private final StateMachineAgent<LeaderContext> leaderStateMachine;
    private LeaderContext leaderContext;

    private final WaitState<ReplicationContext> replicationClosedState = (c) ->
    {
    };

    private final ReplicationState replicationState = new ReplicationState();
    private final CloseReplicationsState closeReplicationsState = new CloseReplicationsState();
    private final ClosingReplicationsState closingReplicationsState = new ClosingReplicationsState();

    private final StateMachineAgent<ReplicationContext> replicationStateMachine;
    private ReplicationContext replicationContext;

    private final List<Member> joiningMembers;
    private final Member joiningMember;

    private boolean sendJoinResponse;
    private int joinChannelId;
    private long joinConnectionId;
    private long joinRequestId;

    private final ConfigurationController configurationController;

    private final MessageWriter messageWriter;


    public LeaderState(final RaftContext context)
    {
        super(context);

        this.joiningMembers = new CopyOnWriteArrayList<>();
        this.joiningMember = new Member();

        this.configurationController = new ConfigurationController(context);

        this.messageWriter = new MessageWriter(context.getSendBuffer());

        this.leaderStateMachine = new StateMachineAgent<>(StateMachine
                .<LeaderContext> builder(s ->
                {
                    leaderContext = new LeaderContext(s, context);
                    leaderContext.configController = configurationController;
                    return leaderContext;
                })
                .initialState(leaderClosedState)

                .from(leaderClosedState).take(TRANSITION_OPEN).to(openAppendControllerState)
                .from(leaderClosedState).take(TRANSITION_CLOSE).to(leaderClosedState)

                .from(openAppendControllerState).take(TRANSITION_DEFAULT).to(appendInitialEntryState)
                .from(openAppendControllerState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

                .from(appendInitialEntryState).take(TRANSITION_DEFAULT).to(openConfigurationControllerState)
                .from(appendInitialEntryState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

                .from(openConfigurationControllerState).take(TRANSITION_DEFAULT).to(leaderInitializedState)
                .from(openConfigurationControllerState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

                .from(leaderInitializedState).take(TRANSITION_CLOSE).to(closeAppendControllerState)

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

                .from(replicationClosedState).take(TRANSITION_OPEN).to(replicationState)
                .from(replicationClosedState).take(TRANSITION_CLOSE).to(replicationClosedState)

                .from(replicationState).take(TRANSITION_CLOSE).to(closeReplicationsState)

                .from(closeReplicationsState).take(TRANSITION_DEFAULT).to(closingReplicationsState)
                .from(closeReplicationsState).take(TRANSITION_CLOSE).to(closeReplicationsState)

                .from(closingReplicationsState).take(TRANSITION_DEFAULT).to(replicationClosedState)
                .from(closingReplicationsState).take(TRANSITION_CLOSE).to(closingReplicationsState)

                .build());
    }

    public int onAppendResponse(final DirectBuffer buffer, final int offset, final int length)
    {
        appendResponse.reset();
        appendResponse.wrap(buffer, offset, length);

        final Member respondedBy = appendResponse.member();
        final boolean succeeded = appendResponse.succeeded();
        final long entryPosition = appendResponse.entryPosition();

        if (respondedBy != null)
        {
            final Member member = raft.getMemberByEndpoint(respondedBy.endpoint());

            if (member != null)
            {
                member.lastContact(System.currentTimeMillis());

                System.out.println("[APPEND RESPONSE] succeeded: " + succeeded + ", entryPosition: " + entryPosition);

                if (succeeded)
                {
                    member.failures(0);
                }
                else
                {
                    member.incrementFailures();
                    member.resetReaderToPreviousEntry(entryPosition);
                }
            }
        }

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

    public int onJoinRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connectionId, final long requestId)
    {
        joinRequest.reset();
        joinRequest.wrap(buffer, offset, length);

        joinResponse.reset();
        boolean respond = false;

        final int id = raft.id();
        final int term = raft.term();

        final Configuration configuration = raft.configuration();
        final List<Member> members = configuration.members();

        if (initializing() || configuring())
        {
            respond = true;
            joinResponse
                .id(id)
                .term(term)
                .succeeded(false)
                .members(members);
        }
        else if (members.contains(joinRequest.member()))
        {
            respond = true;
            joinResponse
                .id(id)
                .term(term)
                .succeeded(true)
                .members(members);
        }
        else
        {
            final Endpoint endpoint = joinRequest.member().endpoint();
            joiningMember.endpoint().wrap(endpoint);

            joiningMembers.clear();
            joiningMembers.addAll(members);
            joiningMembers.add(joiningMember);

            sendJoinResponse = true;
            joinChannelId = channelId;
            joinConnectionId = connectionId;
            joinRequestId = requestId;

            configurationController.open(joiningMembers);
        }

        if (respond)
        {
            messageWriter.protocol(Protocols.REQUEST_RESPONSE)
                .channelId(channelId)
                .connectionId(connectionId)
                .requestId(requestId)
                .message(joinResponse)
                .tryWriteMessage();
        }

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

    public boolean initializing()
    {
        return leaderStateMachine.getCurrentState() != leaderInitializedState;
    }

    public boolean configuring()
    {
        return !configurationController.isClosed();
    }

    @Override
    public Raft.State state()
    {
        return Raft.State.LEADER;
    }

    @Override
    public int doWork()
    {
        int workcount = 0;

        workcount += replicationStateMachine.doWork();
        workcount += leaderStateMachine.doWork();
        workcount += configurationController.doWork();

        if (configurationController.isConfigured() || configurationController.isFailed())
        {
            workcount += 1;

            if (sendJoinResponse)
            {
                joinResponse.reset();
                joinResponse
                    .id(raft.id())
                    .term(raft.term())
                    .succeeded(configurationController.isConfigured())
                    .configurationEntryPosition(raft.configuration().configurationEntryPosition())
                    .configurationEntryTerm(raft.configuration().configurationEntryTerm())
                    .members(raft.configuration().members());

                messageWriter.protocol(Protocols.REQUEST_RESPONSE)
                    .channelId(joinChannelId)
                    .connectionId(joinConnectionId)
                    .requestId(joinRequestId)
                    .message(joinResponse)
                    .tryWriteMessage();

                sendJoinResponse = false;

                joinChannelId = -1;
                joinConnectionId = -1L;
                joinRequestId = -1L;
            }

            configurationController.close();

        }

        return workcount;
    }

    @Override
    public void open()
    {
        if (isLeaderStateMachineClosed())
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
    }

    @Override
    public void close()
    {
        replicationStateMachine.addCommand(CLOSE_REPLICATION_STATE_MACHINE_COMMAND);
        leaderStateMachine.addCommand(CLOSE_LEADER_STATE_MACHINE_COMMAND);
    }

    @Override
    public boolean isClosed()
    {
        return isLeaderStateMachineClosed() && isReplicationStateMachineClosed();
    }

    protected boolean isLeaderStateMachineClosed()
    {
        return leaderStateMachine.getCurrentState() == leaderClosedState;
    }

    protected boolean isReplicationStateMachineClosed()
    {
        return replicationStateMachine.getCurrentState() == replicationClosedState;
    }

    class LeaderContext extends SimpleStateMachineContext
    {
        final RaftContext raftContext;
        final AppendController appendController;
        final InitializeEntry initialEntry;

        ConfigurationController configController;

        LeaderContext(final StateMachine<?> stateMachine, final RaftContext raftContext)
        {
            super(stateMachine);
            this.raftContext = raftContext;
            this.appendController = new AppendController(raftContext);
            this.initialEntry = new InitializeEntry();
        }

        public void reset()
        {
            initialEntry.reset();
        }
    }

    class OpenAppendControllerState implements TransitionState<LeaderContext>
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

    class OpenConfigurationControllerState implements TransitionState<LeaderContext>
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

    class ConfigureState implements State<LeaderContext>
    {
        @Override
        public int doWork(LeaderContext context) throws Exception
        {
            final ConfigurationController configController = context.configController;

            int workcount = 0;

            workcount += configController.doWork();

            if (configController.isConfigured())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }
            else if (configController.isFailed())
            {
                // TODO: what should we do, when we were not able to
                // apply (or commit) current configuration during initialization.
                workcount += 1;
                raft.transition(FOLLOWER);
            }

            return workcount;
        }
    }

    class CloseAppendControllerState implements TransitionState<LeaderContext>
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

    class ClosingAppendControllerState implements State<LeaderContext>
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

    class CloseConfigurationControllerState implements TransitionState<LeaderContext>
    {
        @Override
        public void work(LeaderContext context) throws Exception
        {
            final ConfigurationController configController = context.configController;

            if (configController.isClosed())
            {
                configController.close();
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    class ClosingConfigurationControllerState implements State<LeaderContext>
    {
        @Override
        public int doWork(LeaderContext context) throws Exception
        {
            final ConfigurationController configController = context.configController;

            int workcount = 0;

            workcount += configController.doWork();

            if (configController.isClosed())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    class ReplicationContext extends SimpleStateMachineContext
    {
        final RaftContext raftContext;

        ReplicationContext(StateMachine<?> stateMachine, final RaftContext raftContext)
        {
            super(stateMachine);
            this.raftContext = raftContext;
        }

    }

    class ReplicationState implements State<ReplicationContext>
    {

        @Override
        public int doWork(ReplicationContext context) throws Exception
        {
            final RaftContext raftContext = context.raftContext;
            final Raft raft = raftContext.getRaft();

            final List<Member> members = raft.members();
            final Member self = raft.member();

            int workcount = 0;

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

            return workcount;
        }
    }

    class CloseReplicationsState implements TransitionState<ReplicationContext>
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

    class ClosingReplicationsState implements State<ReplicationContext>
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

}
