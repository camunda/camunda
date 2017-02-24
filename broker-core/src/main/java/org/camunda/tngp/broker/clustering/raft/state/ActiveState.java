package org.camunda.tngp.broker.clustering.raft.state;

import java.util.Random;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.raft.Member;
import org.camunda.tngp.broker.clustering.raft.Raft;
import org.camunda.tngp.broker.clustering.raft.Raft.State;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.transport.protocol.Protocols;

public abstract class ActiveState extends InactiveState
{
    private final Random random = new Random();

    public ActiveState(final RaftContext context)
    {
        super(context);
    }

    protected long randomTimeout(final long min)
    {
        return min + (Math.abs(random.nextLong()) % min);
    }

    public abstract State state();

    public int onVoteRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId, final long connection, final long requestId)
    {
        voteRequest.reset();
        voteRequest.wrap(buffer, offset, length);

        final VoteResponse voteResponse = vote(voteRequest);

        messageWriter.protocol(Protocols.REQUEST_RESPONSE)
            .channelId(channelId)
            .connectionId(connection)
            .requestId(requestId)
            .message(voteResponse)
            .tryWriteMessage();

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

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
        final Member candidate = voteRequest.candidate();

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
            raft.lastVotedFor(candidate.endpoint());
        }

        voteResponse.reset();
        return voteResponse
                .id(raft.id())
                .term(currentTerm)
                .granted(granted);
    }

    protected boolean isLogUpToDate(final long entryPosition, final int entryTerm)
    {
        return logStreamState.isLastReceivedEntry(entryPosition, entryTerm);
    }

    public int onAppendRequest(final DirectBuffer buffer, final int offset, final int length, final int channelId)
    {
        appendRequest.reset();
        appendRequest.wrap(buffer, offset, length);

        final AppendResponse appendResponse = append(appendRequest);

        messageWriter.protocol(Protocols.FULL_DUPLEX_SINGLE_MESSAGE)
            .channelId(channelId)
            .message(appendResponse)
            .tryWriteMessage();

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }

    protected AppendResponse append(final AppendRequest request)
    {
        final int term = request.term();
        final Member leader = request.leader();

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
            System.out.println("\n");
            System.out.println("append");

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

        return appendResponse
                .id(log)
                .term(term)
                .succeeded(succeeded)
                .entryPosition(logPosition)
                .member(raft.member());

    }
}
