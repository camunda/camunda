package org.camunda.tngp.broker.clustering.raft.protocol.controller;

import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.channel.EndpointChannel;
import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.raft.protocol.RaftContext;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.singlemessage.DataFramePool;
import org.camunda.tngp.transport.singlemessage.OutgoingDataFrame;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class AppendController
{
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_OPEN = 1;
    protected static final int TRANSITION_CLOSE = 2;
    protected static final int TRANSITION_FAILED = 3;

    protected final AppendEntriesClosedState appendEntriesClosedState = new AppendEntriesClosedState();
    protected final AppendEntriesClosingState appendEntriesClosingState = new AppendEntriesClosingState();
    protected final AppendEntriesOpeningState appendEntriesOpeningState = new AppendEntriesOpeningState();
    protected final AppendEntriesConnectingState appendEntriesConnectingState = new AppendEntriesConnectingState();
    protected final AppendEntriesSendingState appendEntriesSendingState = new AppendEntriesSendingState();
    protected final AppendEntriesSendedState appendEntriesSendedState = new AppendEntriesSendedState();
    protected final AppendEntriesFailedState appendEntriesFailedState = new AppendEntriesFailedState();

    protected StateMachineAgent<AppendEntriesContext> appendEntriesStateMachine;

    protected final Raft raft;
    protected final ClientChannelManager clientChannelManager;
    protected final TransportConnection connection;
    protected final DataFramePool dataFramePool;

    protected final Member member;

    public AppendController(final Raft raft, final Member member)
    {
        this.raft = raft;
        this.member = member;

        final RaftContext context = raft.context();
        this.clientChannelManager = context.clientChannelManager();
        this.connection = context.connection();
        this.dataFramePool = context.dataFramePool();

        initAppendEntriesStateMachine();
    }

    protected void initAppendEntriesStateMachine()
    {
        appendEntriesStateMachine = new StateMachineAgent<>(StateMachine
                .<AppendEntriesContext> builder(s -> new AppendEntriesContext(s)).initialState(appendEntriesClosedState)

                .from(appendEntriesClosedState).take(TRANSITION_OPEN).to(appendEntriesOpeningState)
                .from(appendEntriesOpeningState).take(TRANSITION_DEFAULT).to(appendEntriesConnectingState)

                .from(appendEntriesConnectingState).take(TRANSITION_DEFAULT).to(appendEntriesSendingState)
                .from(appendEntriesConnectingState).take(TRANSITION_FAILED).to(appendEntriesFailedState)
                .from(appendEntriesConnectingState).take(TRANSITION_CLOSE).to(appendEntriesClosingState)

                .from(appendEntriesSendingState).take(TRANSITION_DEFAULT).to(appendEntriesSendedState)
                .from(appendEntriesSendingState).take(TRANSITION_FAILED).to(appendEntriesFailedState)
                .from(appendEntriesSendingState).take(TRANSITION_CLOSE).to(appendEntriesClosingState)

                .from(appendEntriesSendedState).take(TRANSITION_CLOSE).to(appendEntriesClosingState)
                .from(appendEntriesFailedState).take(TRANSITION_CLOSE).to(appendEntriesClosingState)

                .from(appendEntriesClosingState).take(TRANSITION_DEFAULT).to(appendEntriesClosedState)

                .build());
    }

    public void open()
    {
        appendEntriesStateMachine.addCommand((c) ->
        {

            final boolean opened = c.tryTake(TRANSITION_OPEN);
            if (!opened)
            {
                throw new IllegalStateException("Cannot open state machine. State is not closed.");
            }


        });
    }

    public void close()
    {
        appendEntriesStateMachine.addCommand((c) ->
        {
            final boolean closed = c.tryTake(TRANSITION_CLOSE);
            if (!closed)
            {
                throw new IllegalStateException("Cannot close state machine.");
            }
        });
    }

    public int doWork()
    {
        return appendEntriesStateMachine.doWork();
    }

    public boolean isClosed()
    {
        return appendEntriesStateMachine.getCurrentState() == appendEntriesClosedState;
    }

    public boolean isLastAppendEntriesSended()
    {
        return appendEntriesStateMachine.getCurrentState() == appendEntriesSendedState;
    }


    class AppendEntriesContext extends SimpleStateMachineContext
    {
        protected EndpointChannel endpointChannel;
        protected ClientChannel channel;
        protected final AppendRequest appendRequest = new AppendRequest();

        AppendEntriesContext(final StateMachine<AppendEntriesContext> stateMachine)
        {
            super(stateMachine);
        }
    }

    class AppendEntriesClosedState implements WaitState<AppendEntriesContext>
    {
        @Override
        public void work(final AppendEntriesContext context)
        {
            // nothing to do
        }
    }

    class AppendEntriesClosingState implements TransitionState<AppendEntriesContext>
    {
        @Override
        public void work(final AppendEntriesContext context)
        {
            final EndpointChannel endpointChannel = context.endpointChannel;

            try
            {
                clientChannelManager.reclaim(endpointChannel);
            }
            finally
            {
                context.endpointChannel = null;
                context.channel = null;
                context.take(TRANSITION_DEFAULT);
            }
        }
    }

    class AppendEntriesOpeningState implements TransitionState<AppendEntriesContext>
    {
        @Override
        public void work(final AppendEntriesContext context)
        {
            member.incrementIndex();
            final int index = member.index();

            final int log = raft.stream().getId();
            final int term = raft.term();
            final long previousEntryPosition = member.currentEntryPosition();
            final int previousEntryTerm = member.currentEntryTerm();

            final AppendRequest appendEntriesRequest = context.appendRequest;

            appendEntriesRequest.reset();
            appendEntriesRequest
                .term(term)
                .log(log)
                .previousEntryPosition(previousEntryPosition)
                .previousEntryTerm(previousEntryTerm)
                .index(index)
                .leader(raft.member().endpoint())
                .entry(null);

            if (!member.hasFailures() && member.hasNextEntry())
            {
                final LoggedEvent nextEntry = member.nextEntry();
                appendEntriesRequest.entry(nextEntry);
            }

            member.lastActivity(System.currentTimeMillis());
            context.take(TRANSITION_DEFAULT);
        }
    }

    class AppendEntriesConnectingState implements State<AppendEntriesContext>
    {
        @Override
        public int doWork(final AppendEntriesContext context)
        {
            int workcount = 0;

            EndpointChannel endpointChannel = context.endpointChannel;

            if (endpointChannel == null || endpointChannel.isClosed())
            {
                workcount += 1;

                final Endpoint endpoint = member.endpoint();

                endpointChannel = clientChannelManager.claim(endpoint);
                context.endpointChannel = endpointChannel;
            }

            try
            {
                final ClientChannel channel = endpointChannel.getClientChannel();

                if (channel != null)
                {
                    workcount += 1;

                    context.channel = channel;
                    context.take(TRANSITION_DEFAULT);
                }
            }
            catch (final Exception e)
            {
                workcount += 1;
                member.incrementFailures();
                context.take(TRANSITION_FAILED);
            }

            return workcount;
        }
    }

    class AppendEntriesSendingState implements State<AppendEntriesContext>
    {
        @Override
        public int doWork(final AppendEntriesContext context)
        {
            int workcount = 0;

            final ClientChannel channel = context.channel;
            final AppendRequest appendRequest = context.appendRequest;
            final int messageLength = appendRequest.getLength();

            final OutgoingDataFrame frame = dataFramePool.openFrame(channel.getId(), messageLength);
            if (frame != null)
            {
                workcount += 1;

                try
                {
                    frame.write(appendRequest);
                    frame.commit();
                    context.take(TRANSITION_DEFAULT);
                }
                catch (final Exception e)
                {
                    e.printStackTrace();
                    frame.abort();
                    member.incrementFailures();
                    context.take(TRANSITION_FAILED);

                }
            }

            return workcount;
        }
    }

    class AppendEntriesSendedState implements WaitState<AppendEntriesContext>
    {
        @Override
        public void work(final AppendEntriesContext context)
        {
            // nothing to do!
        }
    }

    class AppendEntriesFailedState implements WaitState<AppendEntriesContext>
    {
        @Override
        public void work(final AppendEntriesContext context)
        {
            // nothing to do!
        }
    }
}
