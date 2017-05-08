package org.camunda.tngp.broker.clustering.raft.controller;

import static org.camunda.tngp.broker.clustering.raft.Raft.State.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.raft.Configuration;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.clustering.raft.message.JoinRequest;
import org.camunda.tngp.broker.clustering.raft.message.JoinResponse;
import org.camunda.tngp.broker.clustering.util.RequestResponseController;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.State;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.StateMachineCommand;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class JoinController
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_CLOSE = 2;
    private static final int TRANSITION_FAIL = 3;
    private static final int TRANSITION_NEXT = 4;
    private static final int TRANSITION_CONFIGURE = 5;
    private static final int TRANSITION_JOINED = 6;

    private static final StateMachineCommand<JoinContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        c.reset();

        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final WaitState<JoinContext> closedState = (c) ->
    {
    };
    private final WaitState<JoinContext> failedState = (c) ->
    {
    };
    private final WaitState<JoinContext> joinedState = (c) ->
    {
    };

    private final OpenRequestState openRequestState = new OpenRequestState();
    private final JoinState joinState = new JoinState();
    private final ConfigureState configureState = new ConfigureState();
    private final CloseRequestState closeRequestState = new CloseRequestState();
    private final ClosingState closingState = new ClosingState();

    private final StateMachineAgent<JoinContext> joinStateMachine;
    private JoinContext joinContext;

    public JoinController(final RaftContext raftContext)
    {
        this.joinStateMachine  = new StateMachineAgent<>(
                StateMachine.<JoinContext> builder(s ->
                {
                    joinContext = new JoinContext(s, raftContext);
                    return joinContext;
                })

                .initialState(closedState)

                .from(closedState).take(TRANSITION_OPEN).to(openRequestState)
                .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                .from(openRequestState).take(TRANSITION_DEFAULT).to(joinState)
                .from(openRequestState).take(TRANSITION_FAIL).to(failedState)
                .from(openRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(joinState).take(TRANSITION_CONFIGURE).to(configureState)
                .from(joinState).take(TRANSITION_DEFAULT).to(closeRequestState)
                .from(joinState).take(TRANSITION_FAIL).to(failedState)
                .from(joinState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(configureState).take(TRANSITION_JOINED).to(joinedState)
                .from(configureState).take(TRANSITION_DEFAULT).to(closeRequestState)
                .from(configureState).take(TRANSITION_FAIL).to(failedState)
                .from(configureState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(closeRequestState).take(TRANSITION_DEFAULT).to(closingState)
                .from(closeRequestState).take(TRANSITION_CLOSE).to(closeRequestState)

                .from(closingState).take(TRANSITION_NEXT).to(openRequestState)
                .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
                .from(closingState).take(TRANSITION_CLOSE).to(closingState)

                .from(joinedState).take(TRANSITION_CLOSE).to(closeRequestState)
                .from(failedState).take(TRANSITION_CLOSE).to(closeRequestState)

                .build()
                );
    }

    public void open(final List<Member> members)
    {
        if (isClosed())
        {
            joinContext.members = members;
            joinContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    public void close()
    {
        joinStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    public int doWork()
    {
        return joinStateMachine.doWork();
    }

    public boolean isJoined()
    {
        return joinStateMachine.getCurrentState() == joinedState;
    }

    public boolean isFailed()
    {
        return joinStateMachine.getCurrentState() == failedState;
    }

    public boolean isClosed()
    {
        return joinStateMachine.getCurrentState() == closedState;
    }

    static class JoinContext extends SimpleStateMachineContext
    {
        final Raft raft;
        final RequestResponseController requestController;

        final JoinRequest joinRequest;
        final JoinResponse joinResponse;

        List<Member> members;
        int position = -1;
        int transitionAfterClosing = TRANSITION_DEFAULT;

        JoinContext(StateMachine<?> stateMachine, final RaftContext raftContext)
        {
            super(stateMachine);
            this.raft = raftContext.getRaft();
            this.requestController = new RequestResponseController(raftContext.getClientChannelPool(), raftContext.getConnections());
            this.joinRequest = new JoinRequest();
            this.joinResponse = new JoinResponse();
        }

        public void reset()
        {
            members = null;
            position = -1;
        }
    }

    static class OpenRequestState implements TransitionState<JoinContext>
    {
        @Override
        public void work(JoinContext context) throws Exception
        {
            final Raft raft = context.raft;
            final RequestResponseController requestController = context.requestController;

            final LogStream logStream = raft.stream();

            final List<Member> members = context.members;
            final Member self = raft.member();

            int position = context.position + 1;
            final JoinRequest joinRequest = context.joinRequest;

            joinRequest.reset();
            joinRequest
                .topicName(logStream.getTopicName())
                .partitionId(logStream.getPartitionId())
                .member(self);

            if (position >= members.size() - 1)
            {
                position = 0;
            }

            if (members.isEmpty())
            {
                context.take(TRANSITION_FAIL);
            }

            final Member member = members.get(position);

            if (member != null)
            {
                requestController.open(member.endpoint(), joinRequest);

                context.position = position;
                context.take(TRANSITION_DEFAULT);
            }
            else
            {
                context.take(TRANSITION_FAIL);
            }
        }

        @Override
        public void onFailure(JoinContext context, Exception e)
        {
            e.printStackTrace();

            context.take(TRANSITION_FAIL);
        }
    }

    static class JoinState implements State<JoinContext>
    {
        @Override
        public int doWork(final JoinContext context)
        {
            final RequestResponseController requestController = context.requestController;

            int workcount = 0;

            workcount += requestController.doWork();

            if (requestController.isResponseAvailable())
            {
                workcount += 1;
                context.take(TRANSITION_CONFIGURE);
            }
            else if (requestController.isFailed())
            {
                context.transitionAfterClosing = TRANSITION_NEXT;
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }
    }

    static class ConfigureState implements TransitionState<JoinContext>
    {
        @Override
        public void work(JoinContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;
            final JoinResponse joinResponse = context.joinResponse;
            final List<Member> cluster = context.members;
            final Raft raft = context.raft;
            final Member self = raft.member();

            final DirectBuffer responseBuffer = requestController.getResponseBuffer();
            final int responseLength = requestController.getResponseLength();
            joinResponse.wrap(responseBuffer, 0, responseLength);

            if (joinResponse.succeeded())
            {
                final long configEntryPosition = joinResponse.configurationEntryPosition();
                final int configEntryTerm = joinResponse.configurationEntryTerm();
                final List<Member> members = joinResponse.members();

                if (members.contains(self))
                {
                    // apply configuration
                    final List<Member> copy = new CopyOnWriteArrayList<>(members);
                    final Configuration newConfiguration = new Configuration(configEntryPosition, configEntryTerm, copy);
                    raft.configure(newConfiguration);

                    // transition to follower state
                    raft.transition(FOLLOWER);

                    context.take(TRANSITION_JOINED);
                }
                else
                {
                    context.take(TRANSITION_FAIL);
                }
            }
            else
            {
                final List<Member> members = joinResponse.members();

                if (members != null)
                {
                    for (int i = 0; i < members.size(); i++)
                    {
                        final Member receivedMember = members.get(i);
                        if (!cluster.contains(receivedMember))
                        {
                            cluster.add(receivedMember);
                        }
                    }

                    int i = 0;
                    while (i < cluster.size())
                    {
                        final Member clusterMember = cluster.get(i);
                        if (!members.contains(clusterMember))
                        {
                            cluster.remove(clusterMember);
                        }
                        else
                        {
                            i++;
                        }
                    }
                }

                context.transitionAfterClosing = TRANSITION_NEXT;
                context.take(TRANSITION_DEFAULT);
            }
        }
    }

    static class CloseRequestState implements TransitionState<JoinContext>
    {
        @Override
        public void work(JoinContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;

            if (!requestController.isClosed())
            {
                requestController.close();
            }

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class ClosingState implements State<JoinContext>
    {
        @Override
        public int doWork(JoinContext context) throws Exception
        {
            final RequestResponseController requestController = context.requestController;

            int workcount = 0;

            workcount += requestController.doWork();

            if (requestController.isClosed())
            {
                context.take(context.transitionAfterClosing);
                context.transitionAfterClosing = TRANSITION_DEFAULT;
            }

            return workcount;
        }
    }

}
