package org.camunda.tngp.broker.clustering.raft.state;

import java.util.List;

import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft.State;

public class CandidateState extends ActiveState
{
    protected static final int STATE_CLOSED = 0;
    protected static final int STATE_OPENING = 1;
    protected static final int STATE_OPEN = 2;

    protected int state = STATE_CLOSED;

    protected final EpochClock clock = new SystemEpochClock();
    protected long electionTimeoutConfig = 350L;

    protected long electionTime = -1L;
    protected long electionTimeout = -1L;

    protected final VoteRequest voteRequest = new VoteRequest();

    public CandidateState(final Raft raft, final LogStreamState logStreamState)
    {
        super(raft, logStreamState);
    }

    @Override
    public State state()
    {
        return Raft.State.CANDIDATE;
    }

    @Override
    public void doOpen()
    {
        startElection();
    }

    @Override
    public void doClose()
    {
        cancelElection();
    }

    @Override
    public int doWork()
    {
        int workcount = 0;
        if (isOpen())
        {
            workcount += executeElection();
        }
        return workcount;
    }

    @Override
    public VoteResponse vote(final VoteRequest request)
    {
        if (updateTermAndLeader(request.term(), null))
        {
            final VoteResponse voteResponse = super.vote(request);
            raft.transition(Raft.State.FOLLOWER);
            return voteResponse;
        }
        else if (request.candidate().equals(raft.member().endpoint()))
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

    protected void startElection()
    {
        raft
            .term(raft.term() + 1)
            .lastVotedFor(raft.member().endpoint());

        final List<Member> votingMembers = raft.members();

        if (votingMembers.size() == 1)
        {
            raft.transition(Raft.State.LEADER);
            return;
        }

        final VoteRequest voteRequest = prepareVoteRequest();

        for (int i = 0; i < votingMembers.size(); i++)
        {
            final Member votingMember = votingMembers.get(i);
            if (!votingMember.equals(raft.member()))
            {
                votingMember.sendVoteRequest(voteRequest);
            }
        }

        electionTime = clock.time();
        electionTimeout = randomTimeout(electionTimeoutConfig);
    }

    protected VoteRequest prepareVoteRequest()
    {
        voteRequest.reset();
        voteRequest
            .log(raft.stream().getId())
            .term(raft.term())
            .lastEntryPosition(logStreamState.lastReceivedPosition())
            .lastEntryTerm(logStreamState.lastReceivedTerm())
            .candidate(raft.member().endpoint());
        return voteRequest;
    }

    protected int executeElection()
    {
        int votes = 1;

        int workcount = 0;

        boolean shouldStepDown = false;
        int currentTerm = raft.term();

        for (int i = 0; i < raft.members().size(); i++)
        {
            final Member member = raft.members().get(i);

            if (member.equals(raft.member()))
            {
                continue;
            }

            workcount += member.doVote();

            final VoteResponse voteResponse = member.getVoteResponse();

            if (voteResponse != null)
            {
                final int term = voteResponse.term();
                if (currentTerm == term)
                {
                    if (voteResponse.granted())
                    {
                        votes += 1;
                    }
                }
                else if (currentTerm < term)
                {
                    currentTerm = term;
                    shouldStepDown = true;
                    break;
                }
            }
        }

        if (shouldStepDown)
        {
            workcount += 1;
            raft.term(currentTerm);
            raft.transition(Raft.State.FOLLOWER);
            System.out.println("[CANDIDATE]: step down from CANDIDATE state, log: " + raft.id() + ", now: " + System.currentTimeMillis());
        }
        if (votes >= (raft.members().size() / 2 + 1))
        {
            System.out.println("[CANDIDATE]: become LEADER, log: " + raft.id() + ", now: " + System.currentTimeMillis());
            workcount += 1;
            raft.transition(Raft.State.LEADER);
        }
        else if (clock.time() >= electionTime + electionTimeout)
        {
            workcount += 1;
            raft.transition(Raft.State.CANDIDATE);
        }

        return workcount;
    }

    protected void cancelElection()
    {
        try
        {
            for (int i = 0; i < raft.members().size(); i++)
            {
                raft.members().get(i).cancelVoteRequest();
            }
        }
        finally
        {
            electionTime = -1L;
            electionTimeout = -1L;
            state = STATE_CLOSED;
        }
    }

    @Override
    public AppendResponse append(final AppendRequest request)
    {
        if (request.term() >= raft.term())
        {
            raft.term(request.term());
            raft.transition(Raft.State.FOLLOWER);
        }
        return super.append(request);
    }
}
