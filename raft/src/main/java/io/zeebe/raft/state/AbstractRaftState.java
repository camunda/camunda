/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.raft.state;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.raft.BufferedLogStorageAppender;
import io.zeebe.raft.Raft;
import io.zeebe.raft.protocol.*;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerOutput;

public abstract class AbstractRaftState
{
    protected final Raft raft;
    protected final BufferedLogStorageAppender appender;
    protected final LogStream logStream;

    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();

    protected final JoinResponse joinResponse = new JoinResponse();
    protected final PollResponse pollResponse = new PollResponse();
    protected final VoteResponse voteResponse = new VoteResponse();
    protected final AppendResponse appendResponse = new AppendResponse();

    protected final BufferedLogStreamReader reader;

    public AbstractRaftState(final Raft raft, final BufferedLogStorageAppender appender)
    {
        this.raft = raft;
        this.logStream = raft.getLogStream();
        this.appender = appender;

        reader = new BufferedLogStreamReader(logStream, true);
    }

    public abstract RaftState getState();

    public void reset()
    {
        appender.reset();
    }

    public void joinRequest(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId, final JoinRequest joinRequest)
    {
        raft.mayStepDown(joinRequest);
        rejectJoinRequest(serverOutput, remoteAddress, requestId);
    }

    public void pollRequest(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId, final PollRequest pollRequest)
    {
        raft.resetElectionTimeout();
        raft.mayStepDown(pollRequest);

        final boolean granted = raft.isTermCurrent(pollRequest) &&
            appender.isAfterOrEqualsLastEvent(pollRequest.getLastEventPosition(), pollRequest.getLastEventTerm());

        if (granted)
        {
            acceptPollRequest(serverOutput, remoteAddress, requestId);
        }
        else
        {
            rejectPollRequest(serverOutput, remoteAddress, requestId);
        }
    }

    public void voteRequest(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId, final VoteRequest voteRequest)
    {
        raft.resetElectionTimeout();
        raft.mayStepDown(voteRequest);

        final boolean granted = raft.isTermCurrent(voteRequest) &&
            raft.canVoteFor(voteRequest) &&
            appender.isAfterOrEqualsLastEvent(voteRequest.getLastEventPosition(), voteRequest.getLastEventTerm());

        if (granted)
        {
            raft.setVotedFor(voteRequest.getSocketAddress());
            acceptVoteRequest(serverOutput, remoteAddress, requestId);
        }
        else
        {
            rejectVoteRequest(serverOutput, remoteAddress, requestId);
        }
    }

    public void appendRequest(final AppendRequest appendRequest)
    {
        raft.mayStepDown(appendRequest);
        rejectAppendRequest(appendRequest, appendRequest.getPreviousEventPosition());
    }

    public void appendResponse(final AppendResponse appendResponse)
    {
        raft.mayStepDown(appendResponse);
    }

    protected void acceptJoinRequest(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId)
    {
        joinResponse
            .reset()
            .setSucceeded(true)
            .setRaft(raft);

        raft.sendResponse(serverOutput, remoteAddress, requestId, joinResponse);
    }

    protected void rejectJoinRequest(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId)
    {
        joinResponse
            .reset()
            .setSucceeded(false)
            .setRaft(raft);

        raft.sendResponse(serverOutput, remoteAddress, requestId, joinResponse);
    }

    protected void acceptPollRequest(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId)
    {
        pollResponse
            .reset()
            .setTerm(raft.getTerm())
            .setGranted(true);

        raft.sendResponse(serverOutput, remoteAddress, requestId, pollResponse);
    }

    protected void rejectPollRequest(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId)
    {
        pollResponse
            .reset()
            .setTerm(raft.getTerm())
            .setGranted(false);

        raft.sendResponse(serverOutput, remoteAddress, requestId, pollResponse);
    }

    protected void acceptVoteRequest(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId)
    {
        voteResponse
            .reset()
            .setTerm(raft.getTerm())
            .setGranted(true);

        raft.sendResponse(serverOutput, remoteAddress, requestId, voteResponse);
    }

    protected void rejectVoteRequest(final ServerOutput serverOutput, final RemoteAddress remoteAddress, final long requestId)
    {
        voteResponse
            .reset()
            .setTerm(raft.getTerm())
            .setGranted(false);

        raft.sendResponse(serverOutput, remoteAddress, requestId, voteResponse);
    }

    protected void rejectAppendRequest(final HasSocketAddress hasSocketAddress, final long position)
    {
        appendResponse
            .reset()
            .setRaft(raft)
            .setPreviousEventPosition(position)
            .setSucceeded(false);

        raft.sendMessage(hasSocketAddress.getSocketAddress(), appendResponse);
    }

}
