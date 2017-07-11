/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.raft.state;

import java.util.List;

import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.broker.clustering.raft.controller.PollController;
import io.zeebe.broker.clustering.raft.message.AppendRequest;
import io.zeebe.broker.clustering.raft.message.AppendResponse;
import io.zeebe.broker.clustering.raft.message.ConfigureRequest;
import io.zeebe.broker.clustering.raft.message.ConfigureResponse;
import io.zeebe.broker.clustering.raft.message.VoteRequest;
import io.zeebe.broker.clustering.raft.message.VoteResponse;
import io.zeebe.broker.clustering.raft.util.Quorum;
import io.zeebe.clustering.gossip.RaftMembershipState;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.StateMachine;
import io.zeebe.util.state.StateMachineAgent;
import io.zeebe.util.state.StateMachineCommand;
import io.zeebe.util.state.TransitionState;
import io.zeebe.util.state.WaitState;

public class FollowerState extends ActiveState
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_CLOSE = 2;

    private static final StateMachineCommand<PollContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final WaitState<PollContext> closedState = (c) ->
    {
    };

    private final OpenPollRequestsState openPollRequestsState = new OpenPollRequestsState();
    private final OpenState openState = new OpenState();
    private final ClosePollRequestsState closePollRequestsState = new ClosePollRequestsState();
    private final ClosingState closingState = new ClosingState();

    private final StateMachineAgent<PollContext> pollStateMachine;
    private PollContext pollContext;

    private long heartbeatTimeoutConfig = 350L;
    private long heartbeatTimeout = -1L;

    private boolean open;

    public FollowerState(final RaftContext context)
    {
        super(context);

        this.pollStateMachine = new StateMachineAgent<>(StateMachine
                .<PollContext> builder(s ->
                {
                    pollContext = new PollContext(s, context);
                    return pollContext;
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
        open = true;
        context.getLogStreamState().reset();
        raft.lastContactNow();
        heartbeatTimeout = randomTimeout(heartbeatTimeoutConfig);

    }

    @Override
    public void close()
    {
        open = false;
        pollStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
        heartbeatTimeout = -1L;
    }

    @Override
    public boolean isClosed()
    {
        return !open && isPollStateMachineClosed();
    }

    protected boolean isPollStateMachineClosed()
    {
        return pollStateMachine.getCurrentState() == closedState;
    }

    @Override
    public int doWork()
    {
        int workcount = 0;

        workcount += pollStateMachine.doWork();

        final long current = System.currentTimeMillis();
        if (current >= (raft.lastContact() + heartbeatTimeout))
        {
            workcount += 1;
            if (open && isPollStateMachineClosed())
            {
                pollContext.take(TRANSITION_OPEN);
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

    static class PollContext extends SimpleStateMachineContext
    {
        final Raft raft;
        final Quorum quorum;

        long electionTimeoutConfig = 350L;
        long electionTime = -1L;
        long electionTimeout = -1L;

        PollContext(final StateMachine<?> stateMachine, final RaftContext context)
        {
            super(stateMachine);
            this.quorum = new Quorum();
            this.raft = context.getRaft();
        }
    }

    class OpenPollRequestsState implements TransitionState<PollContext>
    {
        @Override
        public void work(final PollContext context) throws Exception
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

    static class OpenState implements io.zeebe.util.state.State<PollContext>
    {
        @Override
        public int doWork(PollContext context) throws Exception
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

                raft.lastContactNow();
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

    static class ClosePollRequestsState implements io.zeebe.util.state.State<PollContext>
    {
        @Override
        public int doWork(PollContext context) throws Exception
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

    static class ClosingState implements io.zeebe.util.state.State<PollContext>
    {

        @Override
        public int doWork(PollContext context) throws Exception
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
