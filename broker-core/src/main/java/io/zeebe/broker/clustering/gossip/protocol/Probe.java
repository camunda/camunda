package io.zeebe.broker.clustering.gossip.protocol;

import org.agrona.DirectBuffer;

import io.zeebe.broker.clustering.gossip.GossipContext;
import io.zeebe.broker.clustering.gossip.config.GossipConfiguration;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.message.GossipRequest;
import io.zeebe.broker.clustering.gossip.message.GossipResponse;
import io.zeebe.broker.clustering.gossip.message.ProbeRequest;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.RequestResponseController;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

public class Probe
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_FAILED = 2;
    private static final int TRANSITION_CLOSE = 3;

    private static final StateMachineCommand<ProbeContext> OPEN_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean open = c.tryTake(TRANSITION_OPEN);
        if (!open)
        {
            throw new IllegalStateException("Cannot open disseminator, has not been closed.");
        }
    };

    private static final StateMachineCommand<ProbeContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final GossipContext gossipContext;
    private ProbeContext probeContext;

    private final WaitState<ProbeContext> closedState = (c) ->
    {
    };
    private final WaitState<ProbeContext> acknowledgedState = (c) ->
    {
    };
    private final WaitState<ProbeContext> failedState = (c) ->
    {
    };
    private final CloseRequestState closeRequestState = new CloseRequestState();
    private final ClosingState closingState = new ClosingState();
    private final OpeningState openingState = new OpeningState();
    private final OpenState openState = new OpenState();
    private final ProcessResponseState processResponseState = new ProcessResponseState();
    private final ForwardResponseState sendResponseState = new ForwardResponseState();
    private final StateMachineAgent<ProbeContext> probeStateMachine;

    public Probe(final GossipContext context)
    {
        this.gossipContext = context;
        this.probeStateMachine = new StateMachineAgent<>(
                StateMachine.<ProbeContext> builder(s ->
                {
                    probeContext = new ProbeContext(s);
                    return probeContext;
                })
                        .initialState(closedState)

                        .from(closedState).take(TRANSITION_OPEN).to(openingState)

                        .from(openingState).take(TRANSITION_DEFAULT).to(openState)

                        .from(openState).take(TRANSITION_DEFAULT).to(processResponseState)
                        .from(openState).take(TRANSITION_FAILED).to(failedState)
                        .from(openState).take(TRANSITION_CLOSE).to(closeRequestState)

                        .from(processResponseState).take(TRANSITION_DEFAULT).to(sendResponseState)

                        .from(sendResponseState).take(TRANSITION_DEFAULT).to(acknowledgedState)
                        .from(sendResponseState).take(TRANSITION_FAILED).to(failedState)
                        .from(sendResponseState).take(TRANSITION_CLOSE).to(closeRequestState)

                        .from(acknowledgedState).take(TRANSITION_CLOSE).to(closeRequestState)
                        .from(failedState).take(TRANSITION_CLOSE).to(closeRequestState)

                        .from(closeRequestState).take(TRANSITION_DEFAULT).to(closingState)
                        .from(closingState).take(TRANSITION_DEFAULT).to(closedState)

                        .build());
    }

    public void open(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final ServerOutput output,
            final RemoteAddress requestAddress,
            final long requestId)
    {
        probeContext.requestStreamId = requestAddress.getStreamId();
        probeContext.requestId = requestId;
        probeContext.probeRequest.wrap(buffer, offset, length);

        probeStateMachine.addCommand(OPEN_STATE_MACHINE_COMMAND);
    }

    public void close()
    {
        probeContext.reset();

        probeStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public int doWork()
    {
        return probeStateMachine.doWork();
    }

    public boolean isAcknowledged()
    {
        return probeStateMachine.getCurrentState() == acknowledgedState;
    }

    public boolean isFailed()
    {
        return probeStateMachine.getCurrentState() == failedState;
    }

    public boolean isClosed()
    {
        return probeStateMachine.getCurrentState() == closedState;
    }

    class ProbeContext extends SimpleStateMachineContext
    {
        final PeerList peers;
        final RequestResponseController requestController;

        int requestStreamId;
        long requestId;

        final ProbeRequest probeRequest;
        final GossipRequest gossipRequest;
        final GossipResponse gossipResponse;
        final ServerOutput output;
        final ServerResponse response = new ServerResponse();

        ProbeContext(StateMachine<?> stateMachine)
        {
            super(stateMachine);
            this.peers = gossipContext.getPeers();

            final GossipConfiguration config = gossipContext.getConfig();
            final ClientTransport clientTransport = gossipContext.getClientTransport();
            this.requestController = new RequestResponseController(clientTransport, config.probeTimeout);
            this.output = gossipContext.getServerTransport().getOutput();

            this.probeRequest = new ProbeRequest();
            this.gossipRequest = new GossipRequest();
            this.gossipResponse = new GossipResponse();
        }

        public void reset()
        {
            requestStreamId = -1;
            requestId = -1L;
            probeRequest.reset();
        }

    }

    static class OpeningState implements TransitionState<ProbeContext>
    {
        @Override
        public void work(ProbeContext context) throws Exception
        {
            final GossipRequest gossipRequest = context.gossipRequest;
            final PeerList peers = context.peers;
            final ProbeRequest probeRequest = context.probeRequest;
            final RequestResponseController requestController = context.requestController;

            gossipRequest.peers(peers);

            final SocketAddress target = probeRequest.target();
            requestController.open(target, gossipRequest, context.gossipResponse);
            context.take(TRANSITION_DEFAULT);
        }
    }

    static class OpenState implements State<ProbeContext>
    {
        @Override
        public int doWork(ProbeContext context) throws Exception
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

    static class ProcessResponseState implements TransitionState<ProbeContext>
    {
        @Override
        public void work(ProbeContext context) throws Exception
        {
            final GossipResponse gossipResponse = context.gossipResponse;
            final PeerList peers = context.peers;

            peers.merge(gossipResponse.peers());

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ForwardResponseState implements State<ProbeContext>
    {
        @Override
        public int doWork(ProbeContext context) throws Exception
        {
            final int channelId = context.requestStreamId;
            final long requestId = context.requestId;
            final GossipResponse gossipResponse = context.gossipResponse;

            int workcount = 0;

            context.response.reset()
                .remoteStreamId(channelId)
                .requestId(requestId)
                .writer(gossipResponse);

            final boolean success = context.output.sendResponse(context.response);

            if (success)
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class CloseRequestState implements TransitionState<ProbeContext>
    {
        @Override
        public void work(ProbeContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;

            if (!requestController.isClosed())
            {
                requestController.close();
            }
            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ClosingState implements State<ProbeContext>
    {
        @Override
        public int doWork(ProbeContext context) throws Exception
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
