package org.camunda.tngp.broker.clustering.raft.protocol.controller;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.util.Requestor;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.raft.protocol.RaftContext;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class VoteController
{
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_CLOSE = 2;
    protected static final int TRANSITION_FAILED = 3;

    // RequestVote
    protected final RequestVoteClosedState requestVoteClosedState = new RequestVoteClosedState();
    protected final RequestVoteClosingState requestVoteClosingState = new RequestVoteClosingState();
    protected final RequestVoteOpeningState requestVoteOpeningState = new RequestVoteOpeningState();
    protected final RequestVoteOpenState requestVoteOpenState = new RequestVoteOpenState();
    protected final RequestVoteWrapResponseState requestVoteWrapResponseState = new RequestVoteWrapResponseState();
    protected final RequestVoteResponseAvailable requestVoteResponseAvailable = new RequestVoteResponseAvailable();

    protected StateMachineAgent<RequestVoteContext> requestVoteStateMachine;

    protected final Raft raft;
    protected final Member member;

    protected final ClientChannelManager clientChannelManager;
    protected final TransportConnection connection;

    protected VoteRequest voteRequest;

    public VoteController(final Raft raft, final Member member)
    {
        this.raft = raft;
        this.member = member;

        final RaftContext context = raft.context();
        this.clientChannelManager = context.clientChannelManager();
        this.connection = context.connection();

        initRequestVoteStateMachine();
    }

    protected void initRequestVoteStateMachine()
    {
        requestVoteStateMachine = new StateMachineAgent<>(StateMachine
                .<RequestVoteContext> builder(s -> new RequestVoteContext(s)).initialState(requestVoteClosedState)

                .from(requestVoteClosedState).take(TRANSITION_OPEN).to(requestVoteOpeningState)
                .from(requestVoteOpeningState).take(TRANSITION_DEFAULT).to(requestVoteOpenState)

                .from(requestVoteOpenState).take(TRANSITION_DEFAULT).to(requestVoteWrapResponseState)
                .from(requestVoteOpenState).take(TRANSITION_CLOSE).to(requestVoteClosingState)

                .from(requestVoteWrapResponseState).take(TRANSITION_DEFAULT).to(requestVoteResponseAvailable)
                .from(requestVoteResponseAvailable).take(TRANSITION_CLOSE).to(requestVoteClosingState)
                .from(requestVoteClosingState).take(TRANSITION_DEFAULT).to(requestVoteClosedState)
                .build());
    }

    public void open(final VoteRequest voteRequest)
    {
        requestVoteStateMachine.addCommand((c) ->
        {

            final boolean opened = c.tryTake(TRANSITION_OPEN);
            if (opened)
            {
                this.voteRequest = voteRequest;
            }
            else
            {
                throw new IllegalStateException("Cannot open state machine. State is not closed.");
            }


        });
    }

    public void close()
    {
        requestVoteStateMachine.addCommand((c) ->
        {

            final boolean closed = c.tryTake(TRANSITION_CLOSE);
            if (closed)
            {
                voteRequest = null;
            }
            else
            {
                throw new IllegalStateException("Cannot close state machine.");
            }

        });
    }

    public boolean isClosed()
    {
        return requestVoteStateMachine.getCurrentState() == requestVoteClosedState;
    }

    public boolean isVoteResponseAvailable()
    {
        return requestVoteStateMachine.getCurrentState() == requestVoteResponseAvailable;
    }

    public VoteResponse getVoteResponse()
    {
        if (isVoteResponseAvailable())
        {
            return requestVoteResponseAvailable.getVoteResponse();
        }

        return null;
    }

    public int doWork()
    {
        return requestVoteStateMachine.doWork();
    }

    class RequestVoteContext extends SimpleStateMachineContext
    {
        protected final Requestor requestor;
        protected final VoteResponse voteResponse = new VoteResponse();
        protected final BufferedLogStreamReader streamReader = new BufferedLogStreamReader(raft.stream());

        RequestVoteContext(final StateMachine<RequestVoteContext> stateMachine)
        {
            super(stateMachine);
            this.requestor = new Requestor(clientChannelManager, connection);
        }
    }

    class RequestVoteClosedState implements WaitState<RequestVoteContext>
    {
        @Override
        public void work(final RequestVoteContext context)
        {
            // nothing to do
        }
    }

    class RequestVoteClosingState implements TransitionState<RequestVoteContext>
    {
        @Override
        public void work(final RequestVoteContext context)
        {
            final Requestor requestor = context.requestor;
            try
            {
                requestor.close();
            }
            finally
            {
                member.lastActivity(-1L);
                context.take(TRANSITION_DEFAULT);
            }
        }
    }

    class RequestVoteOpeningState implements TransitionState<RequestVoteContext>
    {
        @Override
        public void work(final RequestVoteContext context)
        {
            final Requestor requestor = context.requestor;
            requestor.begin(member.endpoint(), voteRequest);
            member.lastActivity(System.currentTimeMillis());
            context.take(TRANSITION_DEFAULT);
        }
    }

    class RequestVoteOpenState implements State<RequestVoteContext>
    {
        @Override
        public int doWork(final RequestVoteContext context)
        {
            int workcount = 0;

            final Requestor requestor = context.requestor;

            workcount += requestor.execute();

            if (requestor.isResponseAvailable())
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    class RequestVoteWrapResponseState implements TransitionState<RequestVoteContext>
    {
        @Override
        public void work(final RequestVoteContext context)
        {
            final Requestor requestor = context.requestor;
            final VoteResponse voteResponse = context.voteResponse;

            final DirectBuffer responseBuffer = requestor.getResponseBuffer();
            final int responseLength = requestor.getResponseLength();
            voteResponse.wrap(responseBuffer, 0, responseLength);

            context.take(TRANSITION_DEFAULT);
        }
    }

    class RequestVoteResponseAvailable implements WaitState<RequestVoteContext>
    {
        protected VoteResponse voteResponse;

        @Override
        public void work(final RequestVoteContext context)
        {
            voteResponse = context.voteResponse;
        }

        public VoteResponse getVoteResponse()
        {
            return voteResponse;
        }
    }

}
