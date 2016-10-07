package org.camunda.tngp.broker.clustering.raft.protocol.controller;

import java.util.Iterator;
import java.util.List;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.util.Requestor;
import org.camunda.tngp.broker.clustering.raft.message.JoinRequest;
import org.camunda.tngp.broker.clustering.raft.message.JoinResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Configuration;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.raft.protocol.RaftContext;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.TransitionState;

public class JoinController
{
    protected static final int TRANSITION_DEFAULT = 0;
    protected static final int TRANSITION_FAILED = 1;

    protected final ClosedState closedState = new ClosedState();
    protected final OpeningState openingState = new OpeningState();
    protected final OpenState openState = new OpenState();

    protected StateMachineAgent<JoinContext> stateMachine;

    protected final Raft raft;
    protected final ClientChannelManager clientChannelManager;
    protected final TransportConnection connection;
    protected List<Member> cluster;

    public JoinController(final Raft raft)
    {
        this.raft = raft;

        final RaftContext context = raft.context();
        this.clientChannelManager = context.clientChannelManager();
        this.connection = context.connection();

        this.stateMachine  = new StateMachineAgent<>(
                StateMachine.<JoinContext> builder(s -> new JoinContext(s))
                .initialState(closedState)
                .from(closedState).take(TRANSITION_DEFAULT).to(openingState)
                .from(openingState).take(TRANSITION_DEFAULT).to(openState)
                .from(openState).take(TRANSITION_DEFAULT).to(closedState)
                .from(openState).take(TRANSITION_FAILED).to(openingState)
                .build()
                );
    }

    public void open(final List<Member> cluster)
    {
        this.cluster = cluster;

        stateMachine.addCommand(context ->
        {
            final boolean opening = context.tryTake(TRANSITION_DEFAULT);

            if (!opening)
            {
                throw new IllegalStateException("Cannot open state machine. State is not closed.");
            }
        });
        this.cluster = cluster;
    }

    public void close()
    {
        cluster = null;
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    public boolean isClosed()
    {
        return stateMachine.getCurrentState() == closedState;
    }

    class JoinContext extends SimpleStateMachineContext
    {
        protected Iterator<Member> iterator;
        protected final Requestor requestor;
        protected Member current;

        protected long timeout = 350 * 2;
        protected long lastAction = -1L;

        protected JoinRequest request = new JoinRequest();
        protected JoinResponse response = new JoinResponse();

        JoinContext(final StateMachine<?> stateMachine)
        {
            super(stateMachine);
            this.requestor = new Requestor(clientChannelManager, connection);
        }

    }

    class OpeningState implements TransitionState<JoinContext>
    {
        @Override
        public void work(final JoinContext context)
        {
            Iterator<Member> iterator = context.iterator;
            final Requestor requestor = context.requestor;
            final JoinRequest request = context.request;

            if (iterator == null || !iterator.hasNext())
            {
                iterator = JoinController.this.cluster.iterator();
                context.iterator = iterator;
            }

            final Member current = iterator.next();

            request
                .member(raft.member())
                .log(raft.id());

            requestor.begin(current.endpoint(), request);

            context.current = current;
            context.lastAction = System.currentTimeMillis();
            context.take(TRANSITION_DEFAULT);
        }
    }

    class OpenState implements State<JoinContext>
    {
        @Override
        public int doWork(final JoinContext context)
        {
            int workcount = 0;
            final Requestor requestor = context.requestor;

            workcount += requestor.execute();

            if (requestor.isResponseAvailable())
            {
                final JoinResponse response = context.response;

                final DirectBuffer responseBuffer = requestor.getResponseBuffer();
                final int responseLength = requestor.getResponseLength();
                response.wrap(responseBuffer, 0, responseLength);

                context.requestor.close();

                if (response.status())
                {
                    final long configurationEntryPosition = response.configurationEntryPosition();
                    final int configurationEntryTerm = response.configurationEntryTerm();
                    final List<Member> members = response.members();

                    raft.configure(new Configuration(configurationEntryPosition, configurationEntryTerm, members));
                    raft.transition(raft.member().type());

                    context.take(TRANSITION_DEFAULT);

                    System.out.println("YEAH joined raft cluster " + raft.id() + ", now " + System.currentTimeMillis());
                }
                else
                {
                    final List<Member> members = response.members();
                    if (members != null && !members.isEmpty())
                    {
                        for (int i = 0; i < members.size(); i++)
                        {
                            if (!cluster.contains(members.get(i)))
                            {
                                cluster.add(members.get(i));
                            }
                        }

                        int i = 0;
                        while (i < cluster.size())
                        {
                            final Member member = cluster.get(i);

                            if (!members.contains(member))
                            {
                                cluster.remove(member);
                            }
                            else
                            {
                                i++;
                            }
                        }
                    }

                    context.take(TRANSITION_FAILED);
                }

                workcount += 1;
            }
            else if (requestor.isFailed())
            {
                context.requestor.close();
                context.take(TRANSITION_FAILED);
            }
            else if (System.currentTimeMillis() >= context.lastAction + context.timeout)
            {
                context.requestor.close();
                context.take(TRANSITION_FAILED);
            }

            return workcount;
        }
    }

    class ClosedState implements State<JoinContext>
    {
        @Override
        public int doWork(final JoinContext context)
        {
            return 0; // noop
        }
    }

}
