package org.camunda.tngp.broker.clustering.raft.state;

import java.util.List;

import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.clustering.raft.controller.PollController;
import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureRequest;
import org.camunda.tngp.broker.clustering.raft.message.ConfigureResponse;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.util.Quorum;
import org.camunda.tngp.clustering.gossip.RaftMembershipState;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

public class FollowerState extends ActiveState
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_CLOSE = 2;

    private final WaitState<FollowerContext> closedState = (c) ->
    {
    };

    private final OpenPollRequestsState openPollRequestsState = new OpenPollRequestsState();
    private final OpenState openState = new OpenState();
    private final ClosePollRequestsState closePollRequestsState = new ClosePollRequestsState();
    private final ClosingState closingState = new ClosingState();

    private final StateMachineAgent<FollowerContext> followerPollStateMachine;
    private FollowerContext followerContext;

    private boolean open;

    private long heartbeatTimeoutConfig = 350L;
    private long heartbeatTimeout = -1L;

    public FollowerState(final RaftContext context)
    {
        super(context);

        this.followerPollStateMachine = new StateMachineAgent<>(StateMachine
                .<FollowerContext> builder(s ->
                {
                    followerContext = new FollowerContext(s, context);
                    return followerContext;
                })
                .initialState(closedState)

                .from(closedState).take(TRANSITION_OPEN).to(openPollRequestsState)
                .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                .from(openPollRequestsState).take(TRANSITION_DEFAULT).to(openState)
                .from(openPollRequestsState).take(TRANSITION_CLOSE).to(closePollRequestsState)

                .from(openState).take(TRANSITION_CLOSE).to(closePollRequestsState)

                .from(closePollRequestsState).take(TRANSITION_DEFAULT).to(closingState)
                .from(closePollRequestsState).take(TRANSITION_CLOSE).to(closePollRequestsState)

                .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
                .from(closingState).take(TRANSITION_CLOSE).to(closingState)

                .build());
    }

    @Override
    public void open()
    {
        context.getLogStreamState().reset();
        raft.lastContactNow();
        heartbeatTimeout = randomTimeout(heartbeatTimeoutConfig);
        open = true;
    }

    @Override
    public void close()
    {
        heartbeatTimeout = -1L;
        open = false;
    }

    @Override
    public boolean isClosed()
    {
        return !open;
    }

    @Override
    public int doWork()
    {
        int workcount = 0;

        workcount += followerPollStateMachine.doWork();

        final long current = System.currentTimeMillis();
        if (current >= (raft.lastContact() + heartbeatTimeout))
        {
            workcount += 1;
            if (followerPollStateMachine.getCurrentState() == closedState)
            {
                followerContext.take(TRANSITION_OPEN);
            }
        }

        return workcount;
    }

    @Override
    public ConfigureResponse configure(ConfigureRequest configureRequest)
    {
        heartbeatTimeout = randomTimeout(heartbeatTimeoutConfig);
        return super.configure(configureRequest);
    }

    @Override
    public AppendResponse append(AppendRequest appendRequest)
    {
        heartbeatTimeout = randomTimeout(heartbeatTimeoutConfig);
        return super.append(appendRequest);
    }

    @Override
    public VoteResponse vote(VoteRequest voteRequest)
    {
        final VoteResponse voteResponse = super.vote(voteRequest);

        if (voteResponse.granted())
        {
            heartbeatTimeout = randomTimeout(heartbeatTimeoutConfig);
        }

        return voteResponse;
    }

    @Override
    public RaftMembershipState state()
    {
        return RaftMembershipState.FOLLOWER;
    }

    static class FollowerContext extends SimpleStateMachineContext
    {
        final Raft raft;
        final Quorum quorum;

        long electionTimeoutConfig = 350L;
        long electionTime = -1L;
        long electionTimeout = -1L;

        FollowerContext(final StateMachine<?> stateMachine, final RaftContext context)
        {
            super(stateMachine);
            this.quorum = new Quorum();
            this.raft = context.getRaft();
        }

        public void reset()
        {
            electionTime = -1L;
            electionTimeout = -1L;
        }
    }

    class OpenPollRequestsState implements TransitionState<FollowerContext>
    {
        @Override
        public void work(final FollowerContext context) throws Exception
        {
            final Quorum quorum = context.quorum;
            final Raft raft = context.raft;

            final List<Member> members = raft.members();
            final Member self = raft.member();

            if (members.size() == 0 || (members.size() == 1 && members.contains(self)))
            {
                context.take(TRANSITION_CLOSE);
                raft.transition(RaftMembershipState.CANDIDATE);
                return;
            }

            quorum.open(raft.quorum());

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final PollController pollController = member.getPollController();

                if (!self.equals(member) && pollController != null)
                {
                    pollController.open(quorum);
                }
            }

            context.electionTime = System.currentTimeMillis();
            context.electionTimeout = randomTimeout(context.electionTimeoutConfig);

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class OpenState implements org.camunda.tngp.util.state.State<FollowerContext>
    {
        @Override
        public int doWork(FollowerContext context) throws Exception
        {
            int workcount = 0;

            final Raft raft = context.raft;
            final long electionTime = context.electionTime;
            final long electionTimeout = context.electionTimeout;
            final Quorum quorum = context.quorum;

            final List<Member> members = raft.members();
            final Member self = raft.member();

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final PollController pollController = member.getPollController();

                if (!self.equals(member) && pollController != null)
                {
                    workcount += pollController.doWork();
                }
            }

            if (quorum.isCompleted())
            {
                if (quorum.isElected())
                {
                    // this will close this current state
                    raft.transition(RaftMembershipState.CANDIDATE);
                }
                else
                {
                    raft.lastContactNow();
                }

                context.take(TRANSITION_CLOSE);
            }
            else if (System.currentTimeMillis() >= electionTime + electionTimeout)
            {
                raft.lastContactNow();
                quorum.close();

                workcount += 1;
                context.take(TRANSITION_CLOSE);
            }

            return workcount;
        }
    }

    static class ClosePollRequestsState implements org.camunda.tngp.util.state.State<FollowerContext>
    {
        @Override
        public int doWork(FollowerContext context) throws Exception
        {
            int workcount = 0;

            final Raft raft = context.raft;

            final List<Member> members = raft.members();
            final Member self = raft.member();

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final PollController pollController = member.getPollController();

                if (!self.equals(member) && pollController != null && !pollController.isClosed())
                {
                    workcount += 1;
                    pollController.close();
                }
            }

            context.take(TRANSITION_DEFAULT);

            return workcount;
        }
    }

    static class ClosingState implements org.camunda.tngp.util.state.State<FollowerContext>
    {

        @Override
        public int doWork(FollowerContext context) throws Exception
        {
            final Raft raft = context.raft;

            int workcount = 0;
            int closed = 0;

            final List<Member> members = raft.members();
            final Member self = raft.member();

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final PollController pollController = member.getPollController();

                if (!self.equals(member) && pollController != null)
                {
                    workcount += pollController.doWork();

                    if (pollController.isClosed())
                    {
                        closed += 1;
                    }
                }
            }

            if (members.size() - 1 == closed)
            {
                context.quorum.close();
                context.take(TRANSITION_DEFAULT);
            }

            return workcount;
        }

    }
}
