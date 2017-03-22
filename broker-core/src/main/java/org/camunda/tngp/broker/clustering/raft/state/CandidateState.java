package org.camunda.tngp.broker.clustering.raft.state;

import java.util.List;

import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.Raft.State;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.clustering.raft.controller.VoteController;
import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.util.Quorum;
import org.camunda.tngp.util.state.SimpleStateMachineContext;
import org.camunda.tngp.util.state.StateMachine;
import org.camunda.tngp.util.state.StateMachineAgent;
import org.camunda.tngp.util.state.StateMachineCommand;
import org.camunda.tngp.util.state.TransitionState;
import org.camunda.tngp.util.state.WaitState;

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
    public State state()
    {
        return Raft.State.CANDIDATE;
    }

    @Override
    public AppendResponse append(AppendRequest request)
    {
        if (updateTermAndLeader(request.term(), null))
        {
            raft.transition(State.FOLLOWER);
        }

        return super.append(request);
    }

    @Override
    public VoteResponse vote(VoteRequest voteRequest)
    {
        if (updateTermAndLeader(voteRequest.term(), null))
        {
            raft.transition(State.FOLLOWER);
            return super.vote(voteRequest);
        }

        final Member self = raft.member();
        final Member candidate = voteRequest.candidate();

        if (self.equals(candidate))
        {
            voteResponse.reset();
            return voteResponse.id(raft.id())
                    .id(raft.id())
                    .term(raft.term())
                    .granted(true);
        }
        else
        {
            voteResponse.reset();
            return voteResponse.id(raft.id())
                    .id(raft.id())
                    .term(raft.term())
                    .granted(false);
        }
    }

    class CandidateContext extends SimpleStateMachineContext
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

    class PrepareState implements TransitionState<CandidateContext>
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
                raft.transition(Raft.State.LEADER);
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

    class OpenState implements org.camunda.tngp.util.state.State<CandidateContext>
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
                    raft.transition(Raft.State.LEADER);
                }
                else
                {
                    // this will close this current state
                    raft.transition(Raft.State.FOLLOWER);
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

    class CloseVoteRequestsState implements org.camunda.tngp.util.state.State<CandidateContext>
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

    class ClosingState implements org.camunda.tngp.util.state.State<CandidateContext>
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
