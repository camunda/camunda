package io.zeebe.broker.clustering.raft.state;

import java.util.List;

import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.broker.clustering.raft.controller.VoteController;
import io.zeebe.broker.clustering.raft.message.AppendRequest;
import io.zeebe.broker.clustering.raft.message.AppendResponse;
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

public class CandidateState extends ActiveState
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_CLOSE = 2;
    private static final int TRANSITION_RETRY = 3;

    private static final StateMachineCommand<CandidateContext> CLOSE_STATE_MACHINE_COMMAND = (c) ->
    {
        final boolean closed = c.tryTake(TRANSITION_CLOSE);
        if (!closed)
        {
            throw new IllegalStateException("Cannot close state machine.");
        }
    };

    private final WaitState<CandidateContext> closedState = (c) ->
    {
    };

    private final PrepareState prepareState = new PrepareState();
    private final OpenVoteRequestsState openVoteRequestsState = new OpenVoteRequestsState();
    private final OpenState openState = new OpenState();
    private final CloseVoteRequestsState closeVoteRequestsState = new CloseVoteRequestsState();
    private final ClosingState closingState = new ClosingState();

    private final StateMachineAgent<CandidateContext> candidateStateMachine;
    private CandidateContext candidateContext;

    public CandidateState(final RaftContext context)
    {
        super(context);

        this.candidateStateMachine = new StateMachineAgent<>(StateMachine
                .<CandidateContext> builder(s ->
                {
                    candidateContext = new CandidateContext(s, context);
                    return candidateContext;
                })
                .initialState(closedState)

                .from(closedState).take(TRANSITION_OPEN).to(prepareState)
                .from(closedState).take(TRANSITION_CLOSE).to(closedState)

                .from(prepareState).take(TRANSITION_DEFAULT).to(openVoteRequestsState)
                .from(prepareState).take(TRANSITION_CLOSE).to(closeVoteRequestsState)

                .from(openVoteRequestsState).take(TRANSITION_DEFAULT).to(openState)
                .from(openVoteRequestsState).take(TRANSITION_CLOSE).to(closeVoteRequestsState)

                .from(openState).take(TRANSITION_RETRY).to(prepareState)
                .from(openState).take(TRANSITION_CLOSE).to(closeVoteRequestsState)

                .from(closeVoteRequestsState).take(TRANSITION_DEFAULT).to(closingState)
                .from(closeVoteRequestsState).take(TRANSITION_CLOSE).to(closeVoteRequestsState)

                .from(closingState).take(TRANSITION_DEFAULT).to(closedState)
                .from(closingState).take(TRANSITION_RETRY).to(prepareState)
                .from(closingState).take(TRANSITION_CLOSE).to(closingState)

                .build());
    }

    @Override
    public void open()
    {
        if (isClosed())
        {
            if (logStreamState != null)
            {
                logStreamState.reset();
            }

            candidateContext.take(TRANSITION_OPEN);
        }
        else
        {
            throw new IllegalStateException("Cannot open state machine, has not been closed.");
        }
    }

    @Override
    public void close()
    {
        candidateStateMachine.addCommand(CLOSE_STATE_MACHINE_COMMAND);
    }

    @Override
    public int doWork()
    {
        return candidateStateMachine.doWork();
    }

    @Override
    public boolean isClosed()
    {
        return candidateStateMachine.getCurrentState() == closedState;
    }

    @Override
    public RaftMembershipState state()
    {
        return RaftMembershipState.CANDIDATE;
    }

    @Override
    public AppendResponse append(AppendRequest request)
    {
        if (updateTermAndLeader(request.term(), null))
        {
            raft.transition(RaftMembershipState.FOLLOWER);
        }

        return super.append(request);
    }

    @Override
    public VoteResponse vote(VoteRequest voteRequest)
    {
        if (updateTermAndLeader(voteRequest.term(), null))
        {
            raft.transition(RaftMembershipState.FOLLOWER);
            return super.vote(voteRequest);
        }

        final Member self = raft.member();
        final Member candidate = voteRequest.candidate();

        if (self.equals(candidate))
        {
            voteResponse.reset();
            return voteResponse
                    .term(raft.term())
                    .granted(true);
        }
        else
        {
            voteResponse.reset();
            return voteResponse
                    .term(raft.term())
                    .granted(false);
        }
    }

    static class CandidateContext extends SimpleStateMachineContext
    {
        final Raft raft;
        final Quorum quorum;

        long electionTimeoutConfig = 350L;
        long electionTime = -1L;
        long electionTimeout = -1L;
        int transitionAfterClosing = TRANSITION_DEFAULT;

        CandidateContext(final StateMachine<?> stateMachine, final RaftContext context)
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

    static class PrepareState implements TransitionState<CandidateContext>
    {
        public void work(final CandidateContext context) throws Exception
        {
            final Quorum quorum = context.quorum;
            final Raft raft = context.raft;
            final Member self = raft.member();
            final List<Member> members = raft.members();

            final int currentTerm = raft.term();
            raft.term(currentTerm + 1);
            raft.lastVotedFor(self.endpoint());

            if (members.size() == 1)
            {
                context.take(TRANSITION_CLOSE);
                raft.transition(RaftMembershipState.LEADER);
            }
            else
            {
                quorum.open(raft.quorum());
                context.take(TRANSITION_DEFAULT);
            }
        }
    }

    class OpenVoteRequestsState implements TransitionState<CandidateContext>
    {
        @Override
        public void work(final CandidateContext context) throws Exception
        {
            final Raft raft = context.raft;
            final Quorum quorum = context.quorum;

            final List<Member> members = raft.members();
            final Member self = raft.member();

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final VoteController voteController = member.getVoteController();

                if (!self.equals(member) && voteController != null)
                {
                    voteController.open(quorum);
                }
            }

            context.electionTime = System.currentTimeMillis();
            context.electionTimeout = randomTimeout(context.electionTimeoutConfig);

            context.take(TRANSITION_DEFAULT);
        }
    }

    static class OpenState implements io.zeebe.util.state.State<CandidateContext>
    {
        @Override
        public int doWork(CandidateContext context) throws Exception
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
                final VoteController voteController = member.getVoteController();

                if (!self.equals(member) && voteController != null)
                {
                    workcount += voteController.doWork();
                }
            }

            if (quorum.isCompleted())
            {
                if (quorum.isElected())
                {
                    // this will close this current state
                    raft.transition(RaftMembershipState.LEADER);
                }
                else
                {
                    // this will close this current state
                    raft.transition(RaftMembershipState.FOLLOWER);
                }
            }
            else if (System.currentTimeMillis() >= electionTime + electionTimeout)
            {
                for (int i = 0; i < members.size(); i++)
                {
                    final Member member = members.get(i);
                    final VoteController voteController = member.getVoteController();

                    if (!self.equals(member) && voteController != null)
                    {
                        voteController.closeForcibly();
                    }
                }

                quorum.close();

                workcount += 1;
                context.take(TRANSITION_RETRY);
            }

            return workcount;
        }
    }

    static class CloseVoteRequestsState implements io.zeebe.util.state.State<CandidateContext>
    {
        @Override
        public int doWork(CandidateContext context) throws Exception
        {
            int workcount = 0;

            final Raft raft = context.raft;

            final List<Member> members = raft.members();
            final Member self = raft.member();

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final VoteController voteController = member.getVoteController();

                if (!self.equals(member) && voteController != null && !voteController.isClosed())
                {
                    workcount += 1;
                    voteController.close();
                }
            }

            context.take(TRANSITION_DEFAULT);

            return workcount;
        }
    }

    static class ClosingState implements io.zeebe.util.state.State<CandidateContext>
    {

        @Override
        public int doWork(CandidateContext context) throws Exception
        {
            final Raft raft = context.raft;

            int workcount = 0;
            int closed = 0;

            final List<Member> members = raft.members();
            final Member self = raft.member();

            for (int i = 0; i < members.size(); i++)
            {
                final Member member = members.get(i);
                final VoteController voteController = member.getVoteController();

                if (!self.equals(member) && voteController != null)
                {
                    workcount += voteController.doWork();

                    if (voteController.isClosed())
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
