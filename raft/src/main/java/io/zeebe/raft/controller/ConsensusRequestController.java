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

import static io.zeebe.raft.PollRequestEncoder.lastEventPositionNullValue;
import static io.zeebe.raft.PollRequestEncoder.lastEventTermNullValue;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.raft.Loggers;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftMember;
import io.zeebe.raft.RaftMembers;
import io.zeebe.transport.ClientResponse;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.List;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class ConsensusRequestController {
  private static final Logger LOG = Loggers.RAFT_LOGGER;

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final Raft raft;
  private final RaftMembers raftMembers;

  private BufferedLogStreamReader reader;

  private final ConsensusRequestHandler consensusRequestHandler;
  private final ActorControl actor;

  private int granted;
  private int pendingRequests = -1;

  public ConsensusRequestController(
      final Raft raft,
      ActorControl actorControl,
      final ConsensusRequestHandler consensusRequestHandler) {
    this.actor = actorControl;
    this.raft = raft;
    raftMembers = raft.getRaftMembers();
    this.consensusRequestHandler = consensusRequestHandler;
  }

  public void sendRequest() {
    this.reader = new BufferedLogStreamReader(raft.getLogStream());
    final BufferWriter request = createRequest();
    sendRequestToMembers(request);
  }

  protected BufferWriter createRequest() {
    final LoggedEvent lastEvent = getLastEvent();

    final long lastEventPosition;
    final int lastEventTerm;
    if (lastEvent != null) {
      lastEventPosition = lastEvent.getPosition();
      lastEventTerm = lastEvent.getRaftTerm();
    } else {
      lastEventPosition = lastEventPositionNullValue();
      lastEventTerm = lastEventTermNullValue();
    }

    return consensusRequestHandler.createRequest(raft, lastEventPosition, lastEventTerm);
  }

  protected void sendRequestToMembers(final BufferWriter pollRequest) {
    // always vote for yourself
    granted = 1;
    final String requestName = consensusRequestHandler.requestName();
    final int memberSize = raftMembers.getMemberSize();
    final CompletableActorFuture<Void> grantedFuture = new CompletableActorFuture<>();

    if (memberSize == 0) {
      grantedFuture.complete(null);
    } else {
      sendRequestToMembers(pollRequest, requestName, memberSize, grantedFuture);
    }

    actor.runOnCompletion(
        grantedFuture,
        ((aVoid, throwable) -> {
          if (throwable == null) {
            LOG.debug(
                "{} request successful with {} votes for a quorum of {}",
                requestName,
                granted,
                raft.requiredQuorum());
            consensusRequestHandler.consensusGranted(raft);
          } else {
            LOG.debug(
                "{} request failed with {} votes for a quorum of {}",
                requestName,
                granted,
                raft.requiredQuorum());
            consensusRequestHandler.consensusFailed(raft);
          }
          close();
        }));

    LOG.debug("{} request send to {} other members", requestName, memberSize);
  }

  private void sendRequestToMembers(
      BufferWriter pollRequest,
      String requestName,
      int memberSize,
      CompletableActorFuture<Void> grantedFuture) {
    final List<RaftMember> memberList = raftMembers.getMemberList();

    pendingRequests = memberSize;
    for (int i = 0; i < memberSize; i++) {
      final RaftMember member = memberList.get(i);

      final ActorFuture<ClientResponse> clientRequestActorFuture =
          raft.sendRequest(member.getNodeId(), pollRequest, REQUEST_TIMEOUT);

      actor.runOnCompletion(
          clientRequestActorFuture,
          (clientRequest, throwable) -> {
            pendingRequests--;
            if (throwable == null) {
              final DirectBuffer responseBuffer = clientRequest.getResponseBuffer();

              if (consensusRequestHandler.isResponseGranted(raft, responseBuffer)) {
                granted++;
                if (isGranted() && !grantedFuture.isDone()) {
                  grantedFuture.complete(null);
                }
              }
            } else {
              LOG.debug("Failed to receive {} response from {}", requestName, member.getNodeId());
            }

            if (pendingRequests == 0 && !isGranted()) {
              grantedFuture.completeExceptionally(new RuntimeException("Failed to get quorum."));
            }
          });
    }
  }

  public void close() {
    if (reader != null) {
      reader.close();
      reader = null;
    }
  }

  public LoggedEvent getLastEvent() {
    reader.seekToLastEvent();

    if (reader.hasNext()) {
      return reader.next();
    } else {
      return null;
    }
  }

  public boolean isGranted() {
    return granted >= raft.requiredQuorum();
  }
}
