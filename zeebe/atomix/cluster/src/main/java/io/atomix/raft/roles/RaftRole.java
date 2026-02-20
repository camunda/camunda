/*
 * Copyright 2016-present Open Networking Foundation
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
 * limitations under the License
 */
package io.atomix.raft.roles;

import io.atomix.raft.RaftError;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.protocol.AbstractRaftRequest;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.ForceConfigureRequest;
import io.atomix.raft.protocol.ForceConfigureResponse;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.InternalAppendRequest;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.JoinResponse;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.LeaveResponse;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.ProtocolVersionHandler;
import io.atomix.raft.protocol.RaftRequest;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.protocol.TransferResponse;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.utils.Managed;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Raft role interface. */
public interface RaftRole extends Managed<RaftRole> {

  /**
   * Returns the server state type.
   *
   * @return The server state type.
   */
  Role role();

  /**
   * Handles a configure request.
   *
   * @param request The request to handle.
   * @return A completable future to be completed with the request response.
   */
  CompletableFuture<ConfigureResponse> onConfigure(ConfigureRequest request);

  /**
   * Handles an install request.
   *
   * @param request The request to handle.
   * @return A completable future to be completed with the request response.
   */
  CompletableFuture<InstallResponse> onInstall(InstallRequest request);

  /**
   * Handles a configure request.
   *
   * @param request The request to handle.
   * @return A completable future to be completed with the request response.
   */
  CompletableFuture<ReconfigureResponse> onReconfigure(ReconfigureRequest request);

  /**
   * Handles a force configure request. The request is never received from a leader. This is
   * typically requested to remove a set of (unavailable) members when a quorum is not possible.
   */
  CompletableFuture<ForceConfigureResponse> onForceConfigure(ForceConfigureRequest request);

  /** Handles a request to join the cluster. */
  CompletableFuture<JoinResponse> onJoin(JoinRequest request);

  /** Handles a request to leave the cluster. */
  CompletableFuture<LeaveResponse> onLeave(LeaveRequest request);

  /**
   * Handles a transfer request.
   *
   * @param request The request to handle.
   * @return A completable future to be completed with the request response.
   */
  CompletableFuture<TransferResponse> onTransfer(TransferRequest request);

  /**
   * Handles an append request.
   *
   * @param request The request to handle.
   * @return A completable future to be completed with the request response.
   */
  CompletableFuture<AppendResponse> onAppend(InternalAppendRequest request);

  /**
   * Handles a poll request.
   *
   * @param request The request to handle.
   * @return A completable future to be completed with the request response.
   */
  CompletableFuture<PollResponse> onPoll(PollRequest request);

  /**
   * Handles a vote request.
   *
   * @param request The request to handle.
   * @return A completable future to be completed with the request response.
   */
  CompletableFuture<VoteResponse> onVote(VoteRequest request);

  Either<RaftError, Void> shouldAcceptRequest(RaftRequest request);

  /** A batched append request paired with its response future. */
  record BatchedAppend(InternalAppendRequest request, CompletableFuture<AppendResponse> future) {}

  /**
   * Handles a batch of append requests. The default implementation processes each request
   * individually. Subclasses can override this to batch multiple appends with a single flush.
   */
  default void onBatchAppend(final List<BatchedAppend> batch) {
    for (final var item : batch) {
      onAppend(item.request())
          .whenComplete(
              (response, error) -> {
                if (error == null) {
                  item.future().complete(response);
                } else {
                  item.future().completeExceptionally(error);
                }
              });
    }
  }

  default CompletableFuture<? extends RaftResponse> onRaftRequest(
      final AbstractRaftRequest request) {
    return switch (request) {
      case null -> CompletableFuture.failedFuture(new NullPointerException("Request is null"));
      case final PollRequest pollRequest -> onPoll(pollRequest);
      case final InstallRequest installRequest -> onInstall(installRequest);
      case final AppendRequest appendRequest ->
          onAppend(ProtocolVersionHandler.transform(appendRequest));
      case final VersionedAppendRequest versionedAppendRequest ->
          onAppend(ProtocolVersionHandler.transform(versionedAppendRequest));
      case final TransferRequest transferRequest -> onTransfer(transferRequest);
      case final LeaveRequest leaveRequest -> onLeave(leaveRequest);
      case final JoinRequest joinRequest -> onJoin(joinRequest);
      case final ForceConfigureRequest forceConfigureRequest ->
          onForceConfigure(forceConfigureRequest);
      case final ConfigureRequest configureRequest -> onConfigure(configureRequest);
      case final ReconfigureRequest reconfigureRequest -> onReconfigure(reconfigureRequest);
      case final VoteRequest voteRequest -> onVote(voteRequest);
    };
  }
}
