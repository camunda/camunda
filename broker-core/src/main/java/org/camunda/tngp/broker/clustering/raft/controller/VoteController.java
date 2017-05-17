package org.camunda.tngp.broker.clustering.raft.controller;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.state.LogStreamState;
import org.camunda.tngp.broker.clustering.raft.util.Quorum;
import org.camunda.tngp.broker.clustering.util.RequestResponseController;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.transport.ChannelManager;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.StateMachineCommand;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class VoteController
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_CLOSE = 2;
    private static final int TRANSITION_FAILED = 3;

    private static final StateMachineCommand<VoteContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        c.reset();

        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    protected final WaitState<VoteContext> closedState = (c) ->
    {
    };
    protected final WaitState<VoteContext> responseAvailableState = (c) ->
    {
    };
    protected final WaitState<VoteContext> failedState = (c) ->
    {
    };

    private final PrepareVoteRequestState prepareVoteRequestState = new PrepareVoteRequestState();
    private final OpenRequestState openRequestState = new OpenRequestState();
    private final OpenState openState = new OpenState();
    private final ProcessResponseState processResponseState = new ProcessResponseState();
    private final CloseRequestState closeRequestState = new CloseRequestState();
    private final ClosingState closingState = new ClosingState();

    private final StateMachineAgent<VoteContext> requestVoteStateMachine;
    private VoteContext requestVoteContext;


    public VoteController(final RaftContext raftContext, final Member member)
    {
        this.requestVoteStateMachine = new StateMachineAgent<>(StateMachine
                .<VoteContext> builder(s ->
                {
                    requestVoteContext = new VoteContext(s, raftContext, member);
                    return requestVoteContext;
                })
                .initialState(closedState)

                .from(closedState).take(TRANSITION_OPEN).to(prepareVoteRequestState)
                .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                .from(prepareVoteRequestState).take(TRANSITION_DEFAULT).to(openRequestState)
                .from(prepareVoteRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

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
            requestVoteContext.quorum = quorum;
            requestVoteContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        requestVoteStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
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
        return requestVoteStateMachine.getCurrentState() == closedState;
    }

    public boolean isFailed()
    {
        return requestVoteStateMachine.getCurrentState() == failedState;
    }

    public int doWork()
    {
        return requestVoteStateMachine.doWork();
    }

    static class VoteContext extends SimpleStateMachineContext
    {
        final RaftContext context;
        final RequestResponseController requestController;
        final VoteRequest voteRequest;
        final VoteResponse voteResponse;
        final SocketAddress endpoint;

        Quorum quorum;

        VoteContext(final StateMachine<VoteContext> stateMachine, final RaftContext context, final Member member)
        {
            super(stateMachine);

            this.context = context;

            final ChannelManager clientChannelManager = context.getClientChannelPool();
            final TransportConnectionPool connections = context.getConnections();
            this.requestController = new RequestResponseController(clientChannelManager, connections);

            this.voteRequest = new VoteRequest();
            this.voteResponse = new VoteResponse();

            this.endpoint = new SocketAddress();
            this.endpoint.wrap(member.endpoint());
        }

        public void reset()
        {
            voteRequest.reset();
            voteResponse.reset();
            quorum = null;
        }
    }

    static class PrepareVoteRequestState implements TransitionState<VoteContext>
    {
        @Override
        public void work(VoteContext context) throws Exception
        {
            final VoteRequest voteRequest = context.voteRequest;
            final RaftContext raftContext = context.context;
            final Raft raft = raftContext.getRaft();
            final LogStreamState logStreamState = raftContext.getLogStreamState();

            final LogStream logStream = raft.stream();

            final Member self = raft.member();

            final int term = raft.term();
            final long lastReceivedPosition = logStreamState.lastReceivedPosition();
            final int lastReceivedTerm = logStreamState.lastReceivedTerm();

            voteRequest.reset();
            voteRequest
                .topicName(logStream.getTopicName())
                .partitionId(logStream.getPartitionId())
                .term(term)
                .lastEntryPosition(lastReceivedPosition)
                .lastEntryTerm(lastReceivedTerm)
                .candidate(self);

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class OpenRequestState implements TransitionState<VoteContext>
    {
        @Override
        public void work(VoteContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;
            final SocketAddress endpoint = context.endpoint;
            final VoteRequest voteRequest = context.voteRequest;

            requestController.open(endpoint, voteRequest);

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class OpenState implements State<VoteContext>
    {
        @Override
        public int doWork(VoteContext context) throws Exception
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
                context.quorum.fail();
                workcount += 1;
                context.take(TRANSITION_FAILED);
            }

            return workcount;
        }
    }

    static class ProcessResponseState implements TransitionState<VoteContext>
    {
        @Override
        public void work(VoteContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;
            final VoteResponse voteResponse = context.voteResponse;
            final RaftContext raftContext = context.context;
            final Quorum quorum = context.quorum;

            final Raft raft = raftContext.getRaft();

            final DirectBuffer responseBuffer = requestController.getResponseBuffer();
            final int responseLength = requestController.getResponseLength();

            voteResponse.wrap(responseBuffer, 0, responseLength);

            final int voteResponseTerm = voteResponse.term();
            final int currentTerm = raft.term();

            if (voteResponseTerm > currentTerm)
            {
                raft.term(voteResponseTerm);
                quorum.stepdown();
                context.take(TRANSITION_DEFAULT);
            }
            else if (!voteResponse.granted())
            {
                quorum.fail();
                context.take(TRANSITION_DEFAULT);
            }
            else if (voteResponseTerm != currentTerm)
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

    static class CloseRequestState implements TransitionState<VoteContext>
    {
        @Override
        public void work(VoteContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;

            if (!requestController.isClosed())
            {
                requestController.close();
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ClosingState implements State<VoteContext>
    {
        @Override
        public int doWork(VoteContext context) throws Exception
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
