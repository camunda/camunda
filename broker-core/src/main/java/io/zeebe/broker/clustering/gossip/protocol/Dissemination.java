package io.zeebe.broker.clustering.gossip.protocol;

import static io.zeebe.clustering.gossip.PeerState.ALIVE;

import io.zeebe.broker.clustering.gossip.GossipContext;
import io.zeebe.broker.clustering.gossip.config.GossipConfiguration;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerSelector;
import io.zeebe.broker.clustering.gossip.message.GossipRequest;
import io.zeebe.broker.clustering.gossip.message.GossipResponse;
import io.zeebe.transport.RequestResponseController;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

public class Dissemination
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_FAILED = 2;
    private static final int TRANSITION_CLOSE = 3;

    private static final StateMachineCommand<DisseminationContext> OPEN_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean open = c.tryTake(TRANSITION_OPEN);
        if (!open)
        {
            throw new IllegalStateException("Cannot open disseminator, has not been closed.");
        }
    };

    private static final StateMachineCommand<DisseminationContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        c.reset();

        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final GossipContext gossipContext;
    private DisseminationContext disseminationContext;

    private final WaitState<DisseminationContext> closedState = (c) ->
    {
    };
    private final WaitState<DisseminationContext> acknowledgedState = (c) ->
    {
    };
    private final WaitState<DisseminationContext> failedState = (c) ->
    {
    };
    private final CloseRequestState closeRequestState = new CloseRequestState();
    private final ClosingState closingState = new ClosingState();
    private final SelectPeerState selectPeerState = new SelectPeerState();
    private final OpenRequestState openRequestState = new OpenRequestState();
    private final OpenState openState = new OpenState();
    private final ProcessResponseState processResponseState = new ProcessResponseState();
    private final OpenFailureDetectorState openFailureDetectorState = new OpenFailureDetectorState();
    private final StateMachineAgent<DisseminationContext> disseminationStateMachine;

    public Dissemination(final GossipContext context, final FailureDetection[] failureDetectors)
    {
        this.gossipContext = context;
        this.disseminationStateMachine = new StateMachineAgent<>(
                StateMachine.<DisseminationContext> builder(s ->
                {
                    disseminationContext = new DisseminationContext(s, context.getLocalPeer(), failureDetectors);
                    return disseminationContext;
                })
                        .initialState(closedState)

                        .from(closedState).take(TRANSITION_OPEN).to(selectPeerState)

                        .from(selectPeerState).take(TRANSITION_DEFAULT).to(openRequestState)
                        .from(selectPeerState).take(TRANSITION_CLOSE).to(closingState)

                        .from(openRequestState).take(TRANSITION_DEFAULT).to(openState)

                        .from(openState).take(TRANSITION_DEFAULT).to(processResponseState)
                        .from(openState).take(TRANSITION_FAILED).to(openFailureDetectorState)
                        .from(openState).take(TRANSITION_CLOSE).to(closeRequestState)

                        .from(processResponseState).take(TRANSITION_DEFAULT).to(acknowledgedState)
                        .from(openFailureDetectorState).take(TRANSITION_DEFAULT).to(failedState)

                        .from(acknowledgedState).take(TRANSITION_CLOSE).to(closeRequestState)
                        .from(failedState).take(TRANSITION_CLOSE).to(closeRequestState)

                        .from(closeRequestState).take(TRANSITION_DEFAULT).to(closingState)
                        .from(closingState).take(TRANSITION_DEFAULT).to(closedState)

                        .build());

    }

    public void open()
    {
        disseminationStateMachine.addCommand(OPEN_STATE_MACHINE_COMMAND);
    }

    public void close()
    {
        disseminationStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public int doWork()
    {
        return disseminationStateMachine.doWork();
    }

    public boolean isAcknowledged()
    {
        return disseminationStateMachine.getCurrentState() == acknowledgedState;
    }

    public boolean isFailed()
    {
        return disseminationStateMachine.getCurrentState() == failedState;
    }

    public boolean isClosed()
    {
        return disseminationStateMachine.getCurrentState() == closedState;
    }

    class DisseminationContext extends SimpleStateMachineContext
    {
        final Peer peer;
        final PeerList peers;
        final GossipRequest request;
        final GossipResponse response;
        final RequestResponseController requestController;
        final PeerSelector peerSelector;
        final Peer[] exclusions;
        final FailureDetection[] failureDetectors;

        DisseminationContext(final StateMachine<?> stateMachine, final Peer localPeer, final FailureDetection[] failureDetectors)
        {
            super(stateMachine);

            this.peer = new Peer();
            this.peers = gossipContext.getPeers();
            this.peerSelector = gossipContext.getPeerSelector();

            this.exclusions = new Peer[1];
            this.exclusions[0] = localPeer;

            this.request = new GossipRequest();
            this.response = new GossipResponse();

            final GossipConfiguration config = gossipContext.getConfig();
            this.requestController = new RequestResponseController(gossipContext.getClientTransport(), config.disseminationTimeout);

            this.failureDetectors = failureDetectors;
        }

        public void reset()
        {
            peer.reset();
        }
    }

    class SelectPeerState implements TransitionState<DisseminationContext>
    {
        @Override
        public void work(DisseminationContext context) throws Exception
        {
            final Peer peer = context.peer;
            final PeerSelector peerSelector = context.peerSelector;
            final Peer[] exclusions = context.exclusions;

            if (peerSelector.next(peer, exclusions))
            {
                context.take(TRANSITION_DEFAULT);
            }
            else
            {
                context.take(TRANSITION_CLOSE);
            }
        }
    }

    class OpenRequestState implements TransitionState<DisseminationContext>
    {
        @Override
        public void work(DisseminationContext context) throws Exception
        {
            final GossipRequest request = context.request;
            final PeerList peers = context.peers;
            final RequestResponseController requestController = context.requestController;
            final Peer peer = context.peer;

            request.peers(peers);

            final SocketAddress endpoint = peer.managementEndpoint();
            requestController.open(endpoint, request, context.response);
            context.take(TRANSITION_DEFAULT);
        }
    }

    class OpenState implements State<DisseminationContext>
    {
        @Override
        public int doWork(DisseminationContext context) throws Exception
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

    class ProcessResponseState implements TransitionState<DisseminationContext>
    {
        @Override
        public void work(DisseminationContext context) throws Exception
        {
            final GossipResponse response = context.response;
            final PeerList peers = context.peers;

            peers.merge(response.peers());

            context.take(TRANSITION_DEFAULT);
        }
    }

    class OpenFailureDetectorState implements TransitionState<DisseminationContext>
    {
        @Override
        public void work(DisseminationContext context) throws Exception
        {
            final PeerList peers = context.peers;
            final Peer peer = context.peer;
            final FailureDetection[] failureDetectors = context.failureDetectors;

            final int idx = peers.find(peer);

            if (idx >= 0)
            {
                // get latest state
                peers.get(idx, peer);

                if (peer.state() == ALIVE)
                {
                    FailureDetection detector = null;
                    for (int k = 0; k < failureDetectors.length; k++)
                    {
                        final FailureDetection failureDetection = failureDetectors[k];
                        // if failure detection is already in progress for given peer,
                        // then do not start another failure detection state machine.
                        if (failureDetection.isPeerEqualTo(peer))
                        {
                            detector = null;
                            break;
                        }

                        if (failureDetection.isClosed())
                        {
                            detector = failureDetection;
                        }
                    }

                    if (detector != null)
                    {
                        detector.open(peer);
                    }
                }
            }
            context.take(TRANSITION_DEFAULT);
        }
    }

    class CloseRequestState implements TransitionState<DisseminationContext>
    {
        @Override
        public void work(DisseminationContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;

            if (!requestController.isClosed())
            {
                requestController.close();
            }
            context.take(TRANSITION_DEFAULT);
        }
    }

    class ClosingState implements State<DisseminationContext>
    {
        @Override
        public int doWork(DisseminationContext context) throws Exception
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
