package io.zeebe.broker.clustering.raft.controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import io.zeebe.broker.clustering.raft.Configuration;
import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.broker.clustering.raft.message.LeaveRequest;
import io.zeebe.broker.clustering.raft.message.LeaveResponse;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.transport.RequestResponseController;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

public class LeaveController
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_CLOSE = 2;
    private static final int TRANSITION_FAIL = 3;
    private static final int TRANSITION_NEXT = 4;
    private static final int TRANSITION_CONFIGURE = 5;
    private static final int TRANSITION_LEFT = 6;

    private static final StateMachineCommand<LeaveContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        c.reset();

        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final WaitState<LeaveContext> closedState = (c) ->
    {
    };
    private final WaitState<LeaveContext> failedState = (c) ->
    {
    };
    private final WaitState<LeaveContext> joinedState = (c) ->
    {
    };

    private final OpenRequestState openRequestState = new OpenRequestState();
    private final LeaveState leaveState = new LeaveState();
    private final ConfigureState configureState = new ConfigureState();
    private final CloseRequestState closeRequestState = new CloseRequestState();
    private final ClosingState closingState = new ClosingState();

    private final StateMachineAgent<LeaveContext> leaveStateMachine;
    private LeaveContext leaveContext;

    public LeaveController(final RaftContext raftContext)
    {
        this.leaveStateMachine  = new StateMachineAgent<>(
                StateMachine.<LeaveContext> builder(s ->
                {
                    leaveContext = new LeaveContext(s, raftContext);
                    return leaveContext;
                })

                .initialState(closedState)

                .from(closedState).take(TRANSITION_OPEN).to(openRequestState)
                .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                .from(openRequestState).take(TRANSITION_DEFAULT).to(leaveState)
                .from(openRequestState).take(TRANSITION_FAIL).to(failedState)
                .from(openRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(leaveState).take(TRANSITION_CONFIGURE).to(configureState)
                .from(leaveState).take(TRANSITION_DEFAULT).to(closeRequestState)
                .from(leaveState).take(TRANSITION_FAIL).to(failedState)
                .from(leaveState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(configureState).take(TRANSITION_LEFT).to(joinedState)
                .from(configureState).take(TRANSITION_DEFAULT).to(closeRequestState)
                .from(configureState).take(TRANSITION_FAIL).to(failedState)
                .from(configureState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(closeRequestState).take(TRANSITION_DEFAULT).to(closingState)
                .from(closeRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(closingState).take(TRANSITION_NEXT).to(openRequestState)
                .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
                .from(closingState).take(TRANSITION_CLOSE).to(closingState)

                .from(joinedState).take(TRANSITION_CLOSE).to(closeRequestState)
                .from(failedState).take(TRANSITION_CLOSE).to(closeRequestState)

                .build()
                );
    }

    public void open(final CompletableFuture<Void> future)
    {
        if (isClosed())
        {
            leaveContext.leaveFuture = future;
            leaveContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        leaveStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public int doWork()
    {
        return leaveStateMachine.doWork();
    }

    public boolean isClosed()
    {
        return leaveStateMachine.getCurrentState() == closedState;
    }

    static class LeaveContext extends SimpleStateMachineContext
    {
        final Raft raft;
        final RequestResponseController requestController;

        final LeaveRequest leaveRequest;
        final LeaveResponse leaveResponse;

        CompletableFuture<Void> leaveFuture;

        int position = -1;
        int transitionAfterClosing = TRANSITION_DEFAULT;

        LeaveContext(StateMachine<?> stateMachine, final RaftContext raftContext)
        {
            super(stateMachine);
            this.raft = raftContext.getRaft();
            this.requestController = new RequestResponseController(raftContext.getClientTransport());
            this.leaveRequest = new LeaveRequest();
            this.leaveResponse = new LeaveResponse();
        }

        public void reset()
        {
            position = -1;
        }
    }

    static class OpenRequestState implements TransitionState<LeaveContext>
    {
        @Override
        public void work(LeaveContext context) throws Exception
        {
            final Raft raft = context.raft;
            final RequestResponseController requestController = context.requestController;

            final LogStream logStream = raft.stream();

            final List<Member> members = raft.members();
            final Member self = raft.member();

            int position = context.position + 1;
            final LeaveRequest leaveRequest = context.leaveRequest;

            leaveRequest.reset();
            leaveRequest
                .topicName(logStream.getTopicName())
                .partitionId(logStream.getPartitionId())
                .member(self);

            if (position >= members.size() - 1)
            {
                position = 0;
            }

            if (members.isEmpty())
            {
                context.take(TRANSITION_FAIL);
            }

            final Member member = members.get(position);

            if (member != null)
            {
                context.leaveResponse.reset();
                requestController.open(member.endpoint(), leaveRequest, context.leaveResponse);

                context.position = position;
                context.take(TRANSITION_DEFAULT);
            }
            else
            {
                context.take(TRANSITION_FAIL);
            }
        }
    }

    static class LeaveState implements State<LeaveContext>
    {
        @Override
        public int doWork(final LeaveContext context)
        {
            final RequestResponseController requestController = context.requestController;

            int workcount = 0;

            workcount += requestController.doWork();

            if (requestController.isResponseAvailable())
            {
                workcount += 1;
                context.take(TRANSITION_CONFIGURE);
            }
            else if (requestController.isFailed())
            {
                context.transitionAfterClosing = TRANSITION_NEXT;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class ConfigureState implements TransitionState<LeaveContext>
    {
        @Override
        public void work(LeaveContext context) throws Exception
        {
            final LeaveResponse leaveResponse = context.leaveResponse;
            final Raft raft = context.raft;

            if (leaveResponse.succeeded())
            {
                final long configEntryPosition = leaveResponse.configurationEntryPosition();
                final int configEntryTerm = leaveResponse.configurationEntryTerm();
                final List<Member> members = leaveResponse.members();

                // apply configuration
                final List<Member> copy = new CopyOnWriteArrayList<>(members);
                final Configuration newConfiguration = new Configuration(configEntryPosition, configEntryTerm, copy);
                raft.configure(newConfiguration);

                raft.meta().storeConfiguration(newConfiguration);

                context.leaveFuture.complete(null);
                context.take(TRANSITION_LEFT);
            }
        }
    }

    static class CloseRequestState implements TransitionState<LeaveContext>
    {
        @Override
        public void work(LeaveContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;

            if (!requestController.isClosed())
            {
                requestController.close();
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ClosingState implements State<LeaveContext>
    {
        @Override
        public int doWork(LeaveContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;

            int workcount = 0;

            workcount += requestController.doWork();

            if (requestController.isClosed())
            {
                context.take(context.transitionAfterClosing);
                context.transitionAfterClosing = TRANSITION_DEFAULT;
            }

            return workcount;
        }
    }

}
