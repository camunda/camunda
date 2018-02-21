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

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.raft.Loggers;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftMember;
import io.zeebe.transport.ClientRequest;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

import java.time.Duration;

import static io.zeebe.raft.PollRequestEncoder.lastEventPositionNullValue;
import static io.zeebe.raft.PollRequestEncoder.lastEventTermNullValue;

public class ConsensusRequestController
{
    private static final Logger LOG = Loggers.RAFT_LOGGER;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final Raft raft;
    private BufferedLogStreamReader reader;

    private final ConsensusRequestHandler consensusRequestHandler;
    private final ActorControl actor;

    private int granted;
    private int pendingRequests = -1;

    public ConsensusRequestController(final Raft raft, ActorControl actorControl, final ConsensusRequestHandler consensusRequestHandler)
    {
        this.actor = actorControl;
        this.raft = raft;
        this.consensusRequestHandler = consensusRequestHandler;
    }

    public void sendRequest()
    {
        this.reader = new BufferedLogStreamReader(raft.getLogStream(), true);
        final BufferWriter request = createRequest();
        sendRequestToMembers(request);
    }

    protected BufferWriter createRequest()
    {
        final LoggedEvent lastEvent = getLastEvent();

        final long lastEventPosition;
        final int lastEventTerm;
        if (lastEvent != null)
        {
            lastEventPosition = lastEvent.getPosition();
            lastEventTerm = lastEvent.getRaftTerm();
        }
        else
        {
            lastEventPosition = lastEventPositionNullValue();
            lastEventTerm = lastEventTermNullValue();
        }

        return consensusRequestHandler.createRequest(raft, lastEventPosition, lastEventTerm);
    }

    protected void sendRequestToMembers(final BufferWriter pollRequest)
    {
        // always vote for yourself
        granted = 1;
        final String requestName = consensusRequestHandler.requestName();
        final int memberSize = raft.getMemberSize();
        final CompletableActorFuture<Void> grantedFuture = new CompletableActorFuture<>();

        if (memberSize == 0)
        {
            grantedFuture.complete(null);
        }
        else
        {
            sendRequestToMembers(pollRequest, requestName, memberSize, grantedFuture);
        }

        actor.runOnCompletion(grantedFuture, ((aVoid, throwable) ->
        {
            if (throwable == null)
            {
                LOG.debug("{} request successful with {} votes for a quorum of {}", requestName, granted, raft.requiredQuorum());
                consensusRequestHandler.consensusGranted(raft);
            }
            else
            {
                LOG.debug("{} request failed with {} votes for a quorum of {}", requestName, granted, raft.requiredQuorum());
                consensusRequestHandler.consensusFailed(raft);
            }
            close();
        }));

        LOG.debug("{} request send to {} other members", requestName, memberSize);
    }

    private void sendRequestToMembers(BufferWriter pollRequest, String requestName, int memberSize, CompletableActorFuture<Void> grantedFuture)
    {
        pendingRequests = memberSize;
        for (int i = 0; i < memberSize; i++)
        {
            final RaftMember member = raft.getMember(i);

            final ActorFuture<ClientRequest> clientRequestActorFuture =
                raft.sendRequest(member.getRemoteAddress(), pollRequest, REQUEST_TIMEOUT);

            actor.runOnCompletion(clientRequestActorFuture, (clientRequest, throwable) ->
            {
                pendingRequests--;
                if (throwable == null)
                {
                    final DirectBuffer responseBuffer = clientRequest.join();

                    if (consensusRequestHandler.isResponseGranted(raft, responseBuffer))
                    {
                        granted++;
                        if (isGranted() && !grantedFuture.isDone())
                        {
                            grantedFuture.complete(null);
                        }
                    }
                    clientRequest.close();
                }
                else
                {
                    LOG.debug("Failed to receive {} response", requestName, throwable);
                }

                if (pendingRequests == 0 && !isGranted())
                {
                    grantedFuture.completeExceptionally(new RuntimeException("Failed to get quorum."));
                }
            });
        }
    }

    public void close()
    {
        if (reader != null)
        {
            reader.close();
            reader = null;
        }
    }

    public LoggedEvent getLastEvent()
    {
        reader.seekToLastEvent();

        if (reader.hasNext())
        {
            return reader.next();
        }
        else
        {
            return null;
        }
    }

    public boolean isGranted()
    {
        return granted >= raft.requiredQuorum();
    }

}
