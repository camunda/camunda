package io.zeebe.broker.clustering.raft.controller;

import io.zeebe.broker.clustering.raft.Configuration;
import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.broker.clustering.raft.message.ConfigureRequest;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.transport.RequestResponseController;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

public class ConfigureController
{
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_CLOSE = 2;
    protected static final int TRANSITION_FAILED = 3;
    protected static final int TRANSITION_SCHEDULE = 4;

    private static final StateMachineCommand<ConfigureContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        c.reset();

        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    protected final WaitState<ConfigureContext> closedState = (c) ->
    {
    };

    protected final ScheduleState scheduleState = new ScheduleState();
    protected final OpenRequestState openRequestState = new OpenRequestState();
    protected final OpenState openState = new OpenState();
    protected final CloseRequestState closeRequestState = new CloseRequestState();
    protected final ClosingRequestState closingRequestState = new ClosingRequestState();

    protected final StateMachineAgent<ConfigureContext> configureStateMachine;
    protected ConfigureContext configureContext;

    public ConfigureController(final RaftContext raftContext, final Member member)
    {
        this.configureStateMachine = new StateMachineAgent<>(StateMachine
                .<ConfigureContext> builder(s ->
                {
                    configureContext = new ConfigureContext(s, raftContext, member);
                    return configureContext;
                })
                .initialState(closedState)

                .from(closedState).take(TRANSITION_OPEN).to(scheduleState)
                .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                .from(scheduleState).take(TRANSITION_DEFAULT).to(openRequestState)
                .from(scheduleState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(openRequestState).take(TRANSITION_DEFAULT).to(openState)
                .from(openRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(openState).take(TRANSITION_DEFAULT).to(closeRequestState)
                .from(openState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(closeRequestState).take(TRANSITION_DEFAULT).to(closingRequestState)
                .from(closeRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(closingRequestState).take(TRANSITION_DEFAULT).to(closedState)
                .from(closingRequestState).take(TRANSITION_SCHEDULE).to(scheduleState)
                .from(closingRequestState).take(TRANSITION_CLOSE).to(closingRequestState)

                .build());
    }

    public void open()
    {
        if (isClosed())
        {
            configureContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        configureContext.closing = true;
        configureStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
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
        return configureStateMachine.doWork();
    }

    public boolean isClosed()
    {
        return configureStateMachine.getCurrentState() == closedState;
    }

    static class ConfigureContext extends SimpleStateMachineContext
    {
        final Raft raft;
        final RaftContext raftContext;
        final Member member;
        final ConfigureRequest configureRequest;
        final RequestResponseController requestController;

        boolean closing = false;

        ConfigureContext(final StateMachine<ConfigureContext> stateMachine, final RaftContext raftContext, final Member member)
        {
            super(stateMachine);
            this.raft = raftContext.getRaft();
            this.raftContext = raftContext;
            this.member = member;
            this.configureRequest = new ConfigureRequest();
            this.requestController = new RequestResponseController(raftContext.getClientTransport());
        }

        public void reset()
        {
            configureRequest.reset();
        }
    }

    static class ScheduleState implements State<ConfigureContext>
    {
        @Override
        public int doWork(ConfigureContext context) throws Exception
        {
            int workcount = 0;

            final Member member = context.member;
            final Raft raft = context.raft;
            final ConfigureRequest configureRequest = context.configureRequest;

            final int configEntryTerm = member.configEntryTerm();
            final long configEntryPosition = member.configEntryPosition();

            final LogStream logStream = raft.stream();
            final int term = raft.term();
            final Configuration configuration = raft.configuration();

            if (!member.hasFailures() && (configEntryTerm < term || configEntryPosition < configuration.configurationEntryPosition()))
            {
                configureRequest.reset();
                configureRequest
                    .topicName(logStream.getTopicName())
                    .partitionId(logStream.getPartitionId())
                    .term(term)
                    .configurationEntryTerm(configuration.configurationEntryTerm())
                    .configurationEntryPosition(configuration.configurationEntryPosition())
                    .members(configuration.members());

                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class OpenRequestState implements TransitionState<ConfigureContext>
    {
        @Override
        public void work(ConfigureContext context) throws Exception
        {
            final RequestResponseController controller = context.requestController;
            final Member member = context.member;
            final ConfigureRequest configureRequest = context.configureRequest;

            controller.open(member.endpoint(), configureRequest, null);

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class OpenState implements State<ConfigureContext>
    {
        @Override
        public int doWork(ConfigureContext context) throws Exception
        {
            final ConfigureRequest configureRequest = context.configureRequest;
            final RequestResponseController requestController = context.requestController;
            final Member member = context.member;

            int workcount = 0;

            workcount += requestController.doWork();

            if (requestController.isResponseAvailable())
            {
                member
                    .configEntryPosition(configureRequest.configurationEntryPosition())
                    .configEntryTerm(configureRequest.configurationEntryTerm());

                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }
            else if (requestController.isFailed())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class CloseRequestState implements TransitionState<ConfigureContext>
    {
        @Override
        public void work(ConfigureContext context) throws Exception
        {
            final RequestResponseController controller = context.requestController;

            if (!controller.isClosed())
            {
                controller.close();
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ClosingRequestState implements State<ConfigureContext>
    {
        @Override
        public int doWork(ConfigureContext context) throws Exception
        {
            final RequestResponseController controller = context.requestController;

            int workcount = 0;

            workcount += controller.doWork();

            if (controller.isClosed())
            {
                context.take(context.closing ? TRANSITION_DEFAULT : TRANSITION_SCHEDULE);
            }

            return workcount;
        }
    }

}
