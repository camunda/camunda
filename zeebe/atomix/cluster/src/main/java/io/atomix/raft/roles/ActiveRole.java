/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.roles;

import io.atomix.raft.RaftServer;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.InternalAppendRequest;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import java.util.concurrent.CompletableFuture;

/** Abstract active state. */
public abstract class ActiveRole extends PassiveRole {

  protected ActiveRole(final RaftContext context) {
    super(context);
  }

  @Override
  public CompletableFuture<AppendResponse> onAppend(final InternalAppendRequest request) {
    raft.checkThread();
    logRequest(request);

    // If the request indicates a term that is greater than the current term then
    // assign that term and leader to the current context and transition to follower.
    final boolean transition = updateTermAndLeader(request.term(), request.leader());
    if (transition) {
      // A new leader was elected; the previous leader will no longer complete any in-progress
      // snapshot replication, so we must abort it to avoid waiting forever.
      abortPendingSnapshots();
    }

    // Handle the append request.
    final CompletableFuture<AppendResponse> future = handleAppend(request);

    // If a transition is required then transition back to the follower state.
    // If the node is already a follower then the transition will be ignored.
    if (transition) {
      raft.transition(RaftServer.Role.FOLLOWER);
    }
    return future;
  }

  @Override
  public CompletableFuture<PollResponse> onPoll(final PollRequest request) {
    raft.checkThread();
    logRequest(request);
    updateTermAndLeader(request.term(), null);
    return CompletableFuture.completedFuture(logResponse(handlePoll(request)));
  }

  @Override
  public CompletableFuture<VoteResponse> onVote(final VoteRequest request) {
    raft.checkThread();
    logRequest(request);

    // If the request indicates a term that is greater than the current term then
    // assign that term and leader to the current context.
    final boolean transition = updateTermAndLeader(request.term(), null);

    final CompletableFuture<VoteResponse> future =
        CompletableFuture.completedFuture(logResponse(handleVote(request)));
    if (transition) {
      raft.transition(RaftServer.Role.FOLLOWER);
    }
    return future;
  }
}
