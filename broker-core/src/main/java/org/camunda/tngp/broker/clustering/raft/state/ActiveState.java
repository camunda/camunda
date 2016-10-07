package org.camunda.tngp.broker.clustering.raft.state;

import java.util.Random;

import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.logstreams.log.LoggedEvent;

public abstract class ActiveState extends InactiveState
{
    protected final Random random = new Random();

    public ActiveState(final Raft raft, final LogStreamState logStreamState)
    {
        super(raft, logStreamState);
    }

    protected long randomTimeout(final long min)
    {
        return min + (Math.abs(random.nextLong()) % min);
    }

    @Override
    public VoteResponse vote(final VoteRequest voteRequest)
    {
        final int voteTerm = voteRequest.term();

        final boolean transition = updateTermAndLeader(voteTerm, null);

        final VoteResponse voteResponse = handleVoteRequest(voteRequest);

        if (transition)
        {
            raft.transition(Raft.State.FOLLOWER);
        }

        return voteResponse;
    }

    protected VoteResponse handleVoteRequest(final VoteRequest voteRequest)
    {
        final int currentTerm = raft.term();
        final Endpoint currentLeader = raft.leader();
        final Endpoint lastVotedFor = raft.lastVotedFor();

        final int voteTerm = voteRequest.term();
        final long lastEntryPosition = voteRequest.lastEntryPosition();
        final int lastEntryTerm = voteRequest.lastEntryTerm();
        final Endpoint candidate = voteRequest.candidate();

        boolean granted = false;

        if (voteTerm < currentTerm)
        {
            granted = false;
        }
        else if (currentLeader != null)
        {
            granted = false;
        }
        else if (lastVotedFor == null)
        {
            granted = isLogUpToDate(lastEntryPosition, lastEntryTerm);
        }
        else
        {
            granted = lastVotedFor.equals(candidate);
        }

        if (granted)
        {
            raft.lastVotedFor(candidate);
        }

        voteResponse.reset();
        return voteResponse
            .term(currentTerm)
            .granted(granted);
    }

    protected boolean isLogUpToDate(final long entryPosition, final int entryTerm)
    {
        return logStreamState.isLastReceivedEntry(entryPosition, entryTerm);
    }

    @Override
    public AppendResponse append(final AppendRequest request)
    {
        final int term = request.term();
        final Endpoint leader = request.leader();

        final boolean transition = updateTermAndLeader(term, leader);

        final AppendResponse response = handleAppendRequest(request);

        if (transition)
        {
            raft.transition(Raft.State.FOLLOWER);
        }

        return response;
    }

    protected AppendResponse handleAppendRequest(final AppendRequest request)
    {
        final long currentTerm = raft.term();
        final long appendTerm = request.term();

        if (appendTerm >= currentTerm)
        {
            return appendEntry(request);
        }
        else
        {
            final long position = request.previousEntryPosition();
            return rejectAppendRequest(position);
        }
    }

    protected AppendResponse appendEntry(final AppendRequest request)
    {
        final long requestPreviousEntryPosition = request.previousEntryPosition();
        final int requestPreviousEntryTerm = request.previousEntryTerm();
        final LoggedEvent entry = request.entry();

        if (!logStreamState.isLastReceivedEntry(requestPreviousEntryPosition, requestPreviousEntryTerm))
        {
            logStreamState.discardBufferedEntries();

            if (!logStreamState.isLastWrittenEntry(requestPreviousEntryPosition, requestPreviousEntryTerm))
            {
                if (logStreamState.lastWrittenPosition() > requestPreviousEntryPosition)
                {

                    if (logStreamState.containsEntry(requestPreviousEntryPosition, requestPreviousEntryTerm))
                    {
                        // TODO: (1) truncate only if not (locally) committed (2) truncate only if request.entry() not null
                        logStreamState.setLastWrittenEntry(requestPreviousEntryPosition, requestPreviousEntryTerm);
                    }
                    else
                    {
                        return rejectAppendRequest(requestPreviousEntryPosition);
                    }
                }
                else if (logStreamState.lastWrittenPosition() < requestPreviousEntryPosition)
                {
                    return rejectAppendRequest(logStreamState.lastWrittenPosition() + 1);
                }
                else
                {
                    return rejectAppendRequest(logStreamState.lastWrittenPosition());
                }
            }
        }

        if (entry != null)
        {
            logStreamState.append(entry);
        }

        final long requestCommitPosition = request.commitPosition();
        raft.commitPosition(requestCommitPosition);

        if (logStreamState.shouldFlushBufferedEntries())
        {
            logStreamState.flushBufferedEntries();
        }

        return acknowledgeAppendRequest(logStreamState.lastWrittenPosition());
    }

    protected AppendResponse rejectAppendRequest(final long logPosition)
    {
        return buildAppendResponse(logPosition, false);
    }

    protected AppendResponse acknowledgeAppendRequest(final long logPosition)
    {
        return buildAppendResponse(logPosition, true);
    }

    protected AppendResponse buildAppendResponse(final long logPosition, final boolean succeeded)
    {
        appendResponse.reset();

        final int term = raft.term();
        final int log = raft.stream().getId();
        final Endpoint member = raft.member().endpoint();

        return appendResponse
                .succeeded(succeeded)
                .term(term)
                .log(log)
                .entryPosition(logPosition)
                .member(member);

    }

}
