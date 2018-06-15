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
import io.zeebe.raft.protocol.PollRequest;
import io.zeebe.raft.protocol.PollResponse;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class PollRequestHandler implements ConsensusRequestHandler
{
    private final PollRequest pollRequest = new PollRequest();
    private final PollResponse pollResponse = new PollResponse();

    @Override
    public String requestName()
    {
        return "poll";
    }

    @Override
    public BufferWriter createRequest(final Raft raft, final long lastEventPosition, final int lastTerm)
    {
        return pollRequest.reset()
            .setRaft(raft)
            .setLastEventPosition(lastEventPosition)
            .setLastEventTerm(lastTerm);
    }

    @Override
    public boolean isResponseGranted(final Raft raft, final DirectBuffer responseBuffer)
    {
        pollResponse.wrap(responseBuffer, 0, responseBuffer.capacity());

        // only register response from the current term
        return !raft.mayStepDown(pollResponse) &&
            raft.isTermCurrent(pollResponse) &&
            pollResponse.isGranted();
    }

    @Override
    public void consensusGranted(final Raft raft)
    {
        raft.becomeCandidate(raft.getTerm() + 1);
    }

    @Override
    public void consensusFailed(final Raft raft)
    {

    }

    @Override
    public void reset()
    {
        pollRequest.reset();
        pollResponse.reset();
    }

}
