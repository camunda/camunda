package io.zeebe.broker.clustering.raft.controller;

import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.broker.clustering.raft.message.AppendRequest;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.transport.SingleMessageController;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

public class ReplicationController
{
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_CLOSE = 2;
    protected static final int TRANSITION_FAILED = 3;
    protected static final int TRANSITION_SCHEDULE = 4;

    private static final StateMachineCommand<ReplicationContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        c.reset();

        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    protected final WaitState<ReplicationContext> closedState = (c) ->
    {
    };

    protected final PrepareMessageState prepareMessageState = new PrepareMessageState();
    protected final OpenRequestState openRequestState = new OpenRequestState();
    protected final OpenState openState = new OpenState();
    protected final CloseRequestState closeRequestState = new CloseRequestState();
    protected final ClosingRequestState closingRequestState = new ClosingRequestState();
    protected final ScheduleState scheduleReplicationState = new ScheduleState();

    protected final StateMachineAgent<ReplicationContext> replicationStateMachine;
    protected ReplicationContext replicationContext;

    public ReplicationController(final RaftContext raftContext, final Member member)
    {
        this.replicationStateMachine = new StateMachineAgent<>(StateMachine
                .<ReplicationContext> builder(s ->
                {
                    replicationContext = new ReplicationContext(s, raftContext, member);
                    return replicationContext;
                })
                .initialState(closedState)

                .from(closedState).take(TRANSITION_OPEN).to(prepareMessageState)
                .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                .from(prepareMessageState).take(TRANSITION_DEFAULT).to(openRequestState)
                .from(prepareMessageState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(openRequestState).take(TRANSITION_DEFAULT).to(openState)
                .from(openRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(openState).take(TRANSITION_DEFAULT).to(closeRequestState)
                .from(openState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(scheduleReplicationState).take(TRANSITION_DEFAULT).to(prepareMessageState)
                .from(scheduleReplicationState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(closeRequestState).take(TRANSITION_DEFAULT).to(closingRequestState)
                .from(closeRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(closingRequestState).take(TRANSITION_DEFAULT).to(closedState)
                .from(closingRequestState).take(TRANSITION_SCHEDULE).to(scheduleReplicationState)
                .from(closingRequestState).take(TRANSITION_CLOSE).to(closingRequestState)

                .build());
    }

    public void open()
    {
        if (isClosed())
        {
            replicationContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        replicationContext.closing = true;
        replicationStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public void closeForcibly()
    {
        if (!isClosed())
        {
            close();

            while (!isClosed())
            {
                doWork();
            }
        }
    }

    public int doWork()
    {
        return replicationStateMachine.doWork();
    }

    public boolean isClosed()
    {
        return replicationStateMachine.getCurrentState() == closedState;
    }

    static class ReplicationContext extends SimpleStateMachineContext
    {
        final Raft raft;
        final RaftContext raftContext;
        final Member member;
        final AppendRequest appendRequest;
        final SingleMessageController singleMessageController;

        long lastReplicationTime = -1L;
        long replicationTimeout = 100L;

        boolean closing = false;

        ReplicationContext(final StateMachine<ReplicationContext> stateMachine, final RaftContext raftContext, final Member member)
        {
            super(stateMachine);
            this.raft = raftContext.getRaft();
            this.raftContext = raftContext;
            this.member = member;
            this.appendRequest = new AppendRequest();
            this.singleMessageController = new SingleMessageController(raftContext.getClientTransport());
        }

        public void reset()
        {
            appendRequest.reset();
            lastReplicationTime = -1L;
        }
    }

    static class PrepareMessageState implements TransitionState<ReplicationContext>
    {
        @Override
        public void work(ReplicationContext context) throws Exception
        {
            final Raft raft = context.raft;
            final LogStream logStream = raft.stream();
            final int term = raft.term();
            final Member self = raft.member();

            final Member member = context.member;
            final long previousEntryPosition = member.currentEntryPosition();
            final int previousEntryTerm = member.currentEntryTerm();
            final long commitPosition = raft.commitPosition();

            final AppendRequest appendRequest = context.appendRequest;

            appendRequest.reset();
            appendRequest
                .topicName(logStream.getTopicName())
                .partitionId(logStream.getPartitionId())
                .term(term)
                .previousEntryPosition(previousEntryPosition)
                .previousEntryTerm(previousEntryTerm)
                .commitPosition(commitPosition)
                .leader(self)
                .entry(null);


            if (!member.hasFailures() && member.hasNextEntry())
            {
                final LoggedEvent nextEntry = member.nextEntry();
                appendRequest.entry(nextEntry);
            }

            context.lastReplicationTime = System.currentTimeMillis();
            context.take(TRANSITION_DEFAULT);
        }
    }

    static class OpenRequestState implements TransitionState<ReplicationContext>
    {
        @Override
        public void work(ReplicationContext context) throws Exception
        {
            final SingleMessageController controller = context.singleMessageController;
            final Member member = context.member;
            final AppendRequest appendRequest = context.appendRequest;

            controller.open(member.endpoint(), appendRequest);

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class OpenState implements State<ReplicationContext>
    {
        @Override
        public int doWork(ReplicationContext context) throws Exception
        {
            final SingleMessageController controller = context.singleMessageController;
            final Member member = context.member;

            int workcount = 0;

            workcount += controller.doWork();

            if (controller.isSent())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }
            else if (controller.isFailed())
            {
                member.incrementFailures();

                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class CloseRequestState implements TransitionState<ReplicationContext>
    {
        @Override
        public void work(ReplicationContext context) throws Exception
        {
            final SingleMessageController controller = context.singleMessageController;

            if (!controller.isClosed())
            {
                controller.close();
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ClosingRequestState implements State<ReplicationContext>
    {
        @Override
        public int doWork(ReplicationContext context) throws Exception
        {
            final SingleMessageController controller = context.singleMessageController;

            int workcount = 0;

            workcount += controller.doWork();

            if (controller.isClosed())
            {
                context.take(context.closing ? TRANSITION_DEFAULT : TRANSITION_SCHEDULE);
            }

            return workcount;
        }
    }

    static class ScheduleState implements State<ReplicationContext>
    {
        @Override
        public int doWork(ReplicationContext context) throws Exception
        {
            int workcount = 0;

            final Member member = context.member;
            final long lastReplicationTime = context.lastReplicationTime;
            final long replicationTimeout = context.replicationTimeout;

            if (!member.hasFailures() && member.hasNextEntry())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }
            else if (System.currentTimeMillis() >= lastReplicationTime + replicationTimeout)
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

}
