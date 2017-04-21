package org.camunda.tngp.broker.clustering.raft.controller;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.clustering.raft.message.PollRequest;
import org.camunda.tngp.broker.clustering.raft.message.PollResponse;
import org.camunda.tngp.broker.clustering.raft.state.LogStreamState;
import org.camunda.tngp.broker.clustering.raft.util.Quorum;
import org.camunda.tngp.broker.clustering.util.RequestResponseController;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.StateMachineCommand;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class PollController
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_CLOSE = 2;
    private static final int TRANSITION_FAILED = 3;

    private static final StateMachineCommand<PollContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        c.reset();

        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    protected final WaitState<PollContext> closedState = (c) ->
    {
    };
    protected final WaitState<PollContext> responseAvailableState = (c) ->
    {
    };
    protected final WaitState<PollContext> failedState = (c) ->
    {
    };

    private final PreparePollRequestState preparePollRequestState = new PreparePollRequestState();
    private final OpenRequestState openRequestState = new OpenRequestState();
    private final OpenState openState = new OpenState();
    private final ProcessResponseState processResponseState = new ProcessResponseState();
    private final CloseRequestState closeRequestState = new CloseRequestState();
    private final ClosingState closingState = new ClosingState();

    private final StateMachineAgent<PollContext> requestPollStateMachine;
    private PollContext pollContext;


    public PollController(final RaftContext raftContext, final Member member)
    {
        this.requestPollStateMachine = new StateMachineAgent<>(StateMachine
                .<PollContext> builder(s ->
                {
                    pollContext = new PollContext(s, raftContext, member);
                    return pollContext;
                })
                .initialState(closedState)

                .from(closedState).take(TRANSITION_OPEN).to(preparePollRequestState)
                .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                .from(preparePollRequestState).take(TRANSITION_DEFAULT).to(openRequestState)
                .from(preparePollRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(openRequestState).take(TRANSITION_DEFAULT).to(openState)
                .from(openRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(openState).take(TRANSITION_DEFAULT).to(processResponseState)
                .from(openState).take(TRANSITION_FAILED).to(failedState)
                .from(openState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(processResponseState).take(TRANSITION_DEFAULT).to(responseAvailableState)
                .from(processResponseState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(responseAvailableState).take(TRANSITION_CLOSE).to(closeRequestState)
                .from(failedState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(closeRequestState).take(TRANSITION_DEFAULT).to(closingState)
                .from(closeRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
                .from(closingState).take(TRANSITION_CLOSE).to(closingState)

                .build());
    }

    public void open(final Quorum quorum)
    {
        if (isClosed())
        {
            pollContext.quorum = quorum;
            pollContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        requestPollStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
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

    public boolean isClosed()
    {
        return requestPollStateMachine.getCurrentState() == closedState;
    }

    public boolean isFailed()
    {
        return requestPollStateMachine.getCurrentState() == failedState;
    }

    public int doWork()
    {
        return requestPollStateMachine.doWork();
    }

    static class PollContext extends SimpleStateMachineContext
    {
        final RaftContext context;
        final RequestResponseController requestController;
        final PollRequest pollRequest;
        final PollResponse pollResponse;
        final Endpoint endpoint;

        Quorum quorum;

        PollContext(final StateMachine<PollContext> stateMachine, final RaftContext context, final Member member)
        {
            super(stateMachine);

            this.context = context;

            final ClientChannelManager clientChannelManager = context.getClientChannelManager();
            final TransportConnectionPool connections = context.getConnections();
            this.requestController = new RequestResponseController(clientChannelManager, connections);

            this.pollRequest = new PollRequest();
            this.pollResponse = new PollResponse();

            this.endpoint = new Endpoint();
            this.endpoint.wrap(member.endpoint());
        }

        public void reset()
        {
            pollRequest.reset();
            pollResponse.reset();
            quorum = null;
        }
    }

    static class PreparePollRequestState implements TransitionState<PollContext>
    {
        @Override
        public void work(PollContext context) throws Exception
        {
            final PollRequest pollRequest = context.pollRequest;
            final RaftContext raftContext = context.context;
            final Raft raft = raftContext.getRaft();
            final LogStreamState logStreamState = raftContext.getLogStreamState();

            final Member self = raft.member();

            final int id = raft.id();
            final int term = raft.term();
            final long lastReceivedPosition = logStreamState.lastReceivedPosition();
            final int lastReceivedTerm = logStreamState.lastReceivedTerm();

            pollRequest.reset();
            pollRequest.id(id)
                .term(term)
                .lastEntryPosition(lastReceivedPosition)
                .lastEntryTerm(lastReceivedTerm)
                .candidate(self);

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class OpenRequestState implements TransitionState<PollContext>
    {
        @Override
        public void work(PollContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;
            final Endpoint endpoint = context.endpoint;
            final PollRequest pollRequest = context.pollRequest;

            requestController.open(endpoint, pollRequest);

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class OpenState implements State<PollContext>
    {
        @Override
        public int doWork(PollContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;

            int workcount = 0;

            workcount += requestController.doWork();

            if (requestController.isResponseAvailable())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }
            else if (requestController.isFailed())
            {
                workcount += 1;
                context.take(TRANSITION_FAILED);
            }

            return workcount;
        }
    }

    static class ProcessResponseState implements TransitionState<PollContext>
    {
        @Override
        public void work(PollContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;
            final PollResponse pollResponse = context.pollResponse;
            final RaftContext raftContext = context.context;
            final Quorum quorum = context.quorum;

            final Raft raft = raftContext.getRaft();

            final DirectBuffer responseBuffer = requestController.getResponseBuffer();
            final int responseLength = requestController.getResponseLength();

            pollResponse.wrap(responseBuffer, 0, responseLength);

            final int pollResponseTerm = pollResponse.term();

            if (pollResponseTerm > raft.term())
            {
                raft.term(pollResponseTerm);
                context.take(TRANSITION_DEFAULT);
            }

            if (!pollResponse.granted())
            {
                quorum.fail();
                context.take(TRANSITION_DEFAULT);
            }
            else if (pollResponseTerm != raft.term())
            {
                // voted for another term
                quorum.fail();
                context.take(TRANSITION_DEFAULT);
            }
            else
            {
                quorum.succeed();
                context.take(TRANSITION_DEFAULT);
            }
        }
    }

    static class CloseRequestState implements TransitionState<PollContext>
    {
        @Override
        public void work(PollContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;

            if (!requestController.isClosed())
            {
                requestController.close();
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ClosingState implements State<PollContext>
    {
        @Override
        public int doWork(PollContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;

            int workcount = 0;

            workcount += requestController.doWork();
            if (requestController.isClosed())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }
}
