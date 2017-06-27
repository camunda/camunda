package io.zeebe.broker.clustering.gossip.protocol;

import static io.zeebe.clustering.gossip.PeerState.ALIVE;

import org.agrona.DirectBuffer;

import io.zeebe.broker.clustering.gossip.GossipContext;
import io.zeebe.broker.clustering.gossip.config.GossipConfiguration;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerSelector;
import io.zeebe.broker.clustering.gossip.message.GossipResponse;
import io.zeebe.broker.clustering.gossip.message.ProbeRequest;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RequestResponseController;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

public class FailureDetection
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_FAILED = 2;
    private static final int TRANSITION_CLOSE = 3;

    private static final StateMachineCommand<FailureDetectionContext> OPEN_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean open = c.tryTake(TRANSITION_OPEN);
        if (!open)
        {
            throw new IllegalStateException("Cannot open disseminator, has not been closed.");
        }
    };

    private static final StateMachineCommand<FailureDetectionContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        c.reset();

        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final GossipContext gossipContext;
    private FailureDetectionContext failureDetectionContext;

    private final WaitState<FailureDetectionContext> closedState = (s) ->
    {
    };
    private final WaitState<FailureDetectionContext> acknowledgedState = (s) ->
    {
    };
    private final WaitState<FailureDetectionContext> failedState = (s) ->
    {
    };
    private final CloseRequestsState closeRequestsState = new CloseRequestsState();
    private final ClosingState closingState = new ClosingState();
    private final OpenRequestState openingState = new OpenRequestState();
    private final OpenState openState = new OpenState();
    private final SuspectPeerState suspectPeerState = new SuspectPeerState();
    private final ProcessResponseState processResponseState = new ProcessResponseState();
    private final StateMachineAgent<FailureDetectionContext> failureDetectionStateMachine;

    public FailureDetection(final GossipContext context)
    {
        this.gossipContext = context;
        this.failureDetectionStateMachine = new StateMachineAgent<>(
                StateMachine.<FailureDetectionContext> builder(s ->
                {
                    failureDetectionContext = new FailureDetectionContext(s, context.getLocalPeer());
                    return failureDetectionContext;
                })

                        .initialState(closedState)

                        .from(closedState).take(TRANSITION_OPEN).to(openingState)

                        .from(openingState).take(TRANSITION_DEFAULT).to(openState)

                        .from(openState).take(TRANSITION_DEFAULT).to(processResponseState)
                        .from(openState).take(TRANSITION_FAILED).to(suspectPeerState)
                        .from(openState).take(TRANSITION_CLOSE).to(closeRequestsState)

                        .from(processResponseState).take(TRANSITION_DEFAULT).to(acknowledgedState)
                        .from(suspectPeerState).take(TRANSITION_DEFAULT).to(failedState)

                        .from(acknowledgedState).take(TRANSITION_CLOSE).to(closeRequestsState)
                        .from(failedState).take(TRANSITION_CLOSE).to(closeRequestsState)

                        .from(closeRequestsState).take(TRANSITION_DEFAULT).to(closingState)
                        .from(closingState).take(TRANSITION_DEFAULT).to(closedState)

                        .build());
    }

    public boolean isPeerEqualTo(final Peer peer)
    {
        return failureDetectionContext.peer.managementEndpoint().compareTo(peer.managementEndpoint()) == 0;
    }

    public void open(final Peer peer)
    {
        failureDetectionContext.peer.wrap(peer);
        failureDetectionStateMachine.addCommand(OPEN_STATE_MACHINE_COMMAND);
    }

    public void close()
    {
        failureDetectionStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public int doWork()
    {
        return failureDetectionStateMachine.doWork();
    }


    public boolean isClosed()
    {
        return failureDetectionStateMachine.getCurrentState() == closedState;
    }

    public boolean isAcknowledged()
    {
        return failureDetectionStateMachine.getCurrentState() == acknowledgedState;
    }

    public boolean isFailed()
    {
        return failureDetectionStateMachine.getCurrentState() == failedState;
    }

    class FailureDetectionContext extends SimpleStateMachineContext
    {
        DirectBuffer responseBuffer;
        int responseLength;

        final PeerList peers;

        final PeerSelector peerSelector;
        final RequestResponseController[] requestControllers;
        final Peer peer;
        final Peer[] targets;
        int targetLength;
        final Peer[] exclusions;

        final ProbeRequest request;
        final GossipResponse response;

        FailureDetectionContext(StateMachine<?> stateMachine, final Peer localPeer)
        {
            super(stateMachine);

            this.peer = new Peer();
            this.peers = gossipContext.getPeers();

            this.request = new ProbeRequest();
            this.response = new GossipResponse();

            final ClientTransport clientTransport = gossipContext.getClientTransport();
            final GossipConfiguration config = gossipContext.getConfig();
            final PeerSelector peerSelector = gossipContext.getPeerSelector();

            final int capacity = config.failureDetectionCapacity;

            this.peerSelector = peerSelector;
            this.requestControllers = new RequestResponseController[capacity];
            this.targets = new Peer[capacity];
            this.targetLength = 0;
            this.exclusions = new Peer[2];
            this.exclusions[0] = localPeer;
            this.exclusions[1] = peer;

            for (int i = 0; i < capacity; i++)
            {
                targets[i] = new Peer();
                requestControllers[i] = new RequestResponseController(clientTransport, config.failureDetectorTimeout);
            }
        }

        public void reset()
        {
            for (int i = 0; i < targets.length; i++)
            {
                targets[i].reset();
            }
            targetLength = 0;
            responseBuffer = null;
            responseLength = 0;
            request.reset();
        }
    }

    class SelectPeersState implements TransitionState<FailureDetectionContext>
    {
        @Override
        public void work(FailureDetectionContext context) throws Exception
        {
            final PeerSelector peerSelector = context.peerSelector;
            final Peer[] targets = context.targets;
            final Peer[] exclusions = context.exclusions;

            context.targetLength = peerSelector.next(targets.length, targets, exclusions);
            context.take(TRANSITION_DEFAULT);
        }
    }

    class OpenRequestState implements TransitionState<FailureDetectionContext>
    {
        @Override
        public void work(FailureDetectionContext context) throws Exception
        {
            final ProbeRequest request = context.request;
            final Peer peer = context.peer;
            final int targetLength = context.targetLength;
            final RequestResponseController[] requestControllers = context.requestControllers;
            final Peer[] targets = context.targets;

            request.reset();
            request.target(peer.managementEndpoint());

            for (int i = 0; i < targetLength; i++)
            {
                final RequestResponseController controller = requestControllers[i];
                final Peer target = targets[i];
                controller.open(target.managementEndpoint(), request, null);
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    class OpenState implements State<FailureDetectionContext>
    {
        @Override
        public int doWork(FailureDetectionContext context) throws Exception
        {
            final RequestResponseController[] requestControllers = context.requestControllers;
            final int targetLength = context.targetLength;

            int workcount = 0;
            int failed = 0;

            for (int i = 0; i < targetLength; i++)
            {
                final RequestResponseController controller = requestControllers[i];
                workcount += controller.doWork();

                if (controller.isResponseAvailable())
                {
                    workcount += 1;

                    context.responseBuffer = controller.getResponseBuffer();
                    context.responseLength = controller.getResponseLength();

                    context.take(TRANSITION_DEFAULT);

                    break;
                }
                else if (controller.isFailed())
                {
                    failed += 1;
                }
            }


            if (failed == targetLength)
            {
                workcount += 1;
                context.take(TRANSITION_FAILED);
            }

            return workcount;
        }
    }

    class ProcessResponseState implements TransitionState<FailureDetectionContext>
    {
        @Override
        public void work(FailureDetectionContext context) throws Exception
        {
            final GossipResponse response = context.response;
            final PeerList peers = context.peers;
            final DirectBuffer responseBuffer = context.responseBuffer;
            final int responseLength = context.responseLength;

            response.wrap(responseBuffer, 0, responseLength);
            peers.merge(response.peers());

            context.take(TRANSITION_DEFAULT);
        }
    }

    class SuspectPeerState implements TransitionState<FailureDetectionContext>
    {
        @Override
        public void work(FailureDetectionContext context) throws Exception
        {
            final PeerList peers = context.peers;
            final Peer peer = context.peer;

            final int idx = peers.find(peer);
            if (idx >= 0)
            {
                peers.get(idx, peer);
                if (peer.state() == ALIVE)
                {
                    peer.suspect();
                    peers.set(idx, peer);
                }
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    class CloseRequestsState implements TransitionState<FailureDetectionContext>
    {
        @Override
        public void work(FailureDetectionContext context) throws Exception
        {
            final RequestResponseController[] requestControllers = context.requestControllers;
            final int targetLength = context.targetLength;

            for (int i = 0; i < targetLength; i++)
            {
                final RequestResponseController controller = requestControllers[i];
                if (!controller.isClosed())
                {
                    controller.close();
                }
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    class ClosingState implements State<FailureDetectionContext>
    {

        @Override
        public int doWork(FailureDetectionContext context) throws Exception
        {
            final RequestResponseController[] requestControllers = context.requestControllers;

            int workcount = 0;
            int closed = 0;

            for (int i = 0; i < requestControllers.length; i++)
            {
                final RequestResponseController controller = requestControllers[i];
                workcount += controller.doWork();

                if (controller.isClosed())
                {
                    closed += 1;
                    workcount += 1;
                }
            }

            if (requestControllers.length == closed)
            {
                workcount += 1;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

}
