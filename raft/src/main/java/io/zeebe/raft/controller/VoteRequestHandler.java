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
package io.zeebe.raft.controller;

import io.zeebe.raft.Raft;
import io.zeebe.raft.protocol.VoteRequest;
import io.zeebe.raft.protocol.VoteResponse;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class VoteRequestHandler implements ConsensusRequestHandler
{
    private final VoteRequest voteRequest = new VoteRequest();
    private final VoteResponse voteResponse = new VoteResponse();

    @Override
    public String requestName()
    {
        return "vote";
    }

    @Override
    public BufferWriter createRequest(final Raft raft, final long lastEventPosition, final int lastTerm)
    {
        return voteRequest.reset()
                          .setRaft(raft)
                          .setLastEventPosition(lastEventPosition)
                          .setLastEventTerm(lastTerm);
    }

    @Override
    public boolean isResponseGranted(final Raft raft, final DirectBuffer responseBuffer)
    {
        voteResponse.wrap(responseBuffer, 0, responseBuffer.capacity());

        return voteResponse.isGranted();
    }

    @Override
    public void reset()
    {
        voteRequest.reset();
        voteResponse.reset();
    }

    @Override
    public void consensusGranted(final Raft raft)
    {
        raft.becomeLeader();
    }

    @Override
    public void consensusFailed(final Raft raft)
    {
        raft.becomeFollower();
    }

}
