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

import io.atomix.raft.RaftError;
import io.atomix.raft.RaftError.Type;
import io.atomix.raft.RaftServer;
import io.atomix.raft.impl.RaftContext;
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
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.protocol.TransferResponse;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.raft.storage.system.Configuration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Inactive state. */
public class InactiveRole extends AbstractRole {

  public InactiveRole(final RaftContext context) {
    super(context);
  }

  @Override
  public RaftServer.Role role() {
    return RaftServer.Role.INACTIVE;
  }

  @Override
  public CompletableFuture<ConfigureResponse> onConfigure(final ConfigureRequest request) {
    raft.checkThread();
    logRequest(request);
    updateTermAndLeader(request.term(), request.leader());

    final Configuration configuration =
        new Configuration(
            request.index(),
            request.term(),
            request.timestamp(),
            request.newMembers(),
            request.oldMembers() != null ? request.oldMembers() : List.of(),
            false,
            request.getCompactionBound());

    // Configure the cluster membership. This will cause this server to transition to the
    // appropriate state if its type has changed.
    raft.getCluster().configure(configuration);

    // If the configuration is already committed, commit it to disk.
    // Check against the actual cluster Configuration rather than the received configuration in
    // case the received configuration was an older configuration that was not applied.
    if (raft.getCommitIndex() >= raft.getCluster().getConfiguration().index()) {
      raft.getCluster().commitCurrentConfiguration();
    }

    raft.setCompactionBound(request.getCompactionBound());

    return CompletableFuture.completedFuture(
        logResponse(ConfigureResponse.builder().withStatus(RaftResponse.Status.OK).build()));
  }

  @Override
  public CompletableFuture<InstallResponse> onInstall(final InstallRequest request) {
    logRequest(request);
    final var result =
        logResponse(
            InstallResponse.builder()
                .withStatus(Status.ERROR)
                .withError(RaftError.Type.UNAVAILABLE)
                .build());
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<ReconfigureResponse> onReconfigure(final ReconfigureRequest request) {
    logRequest(request);
    final var result =
        logResponse(
            ReconfigureResponse.builder()
                .withStatus(Status.ERROR)
                .withError(RaftError.Type.UNAVAILABLE)
                .build());
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<ForceConfigureResponse> onForceConfigure(
      final ForceConfigureRequest request) {
    logRequest(request);
    final var result =
        logResponse(
            ForceConfigureResponse.builder()
                .withStatus(Status.ERROR)
                .withError(RaftError.Type.UNAVAILABLE)
                .build());
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<JoinResponse> onJoin(final JoinRequest request) {
    logRequest(request);
    final var result =
        logResponse(
            JoinResponse.builder().withStatus(Status.ERROR).withError(Type.UNAVAILABLE).build());
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<LeaveResponse> onLeave(final LeaveRequest request) {
    logRequest(request);
    final var result =
        logResponse(
            LeaveResponse.builder().withStatus(Status.ERROR).withError(Type.UNAVAILABLE).build());
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<TransferResponse> onTransfer(final TransferRequest request) {
    logRequest(request);
    final var result =
        logResponse(
            TransferResponse.builder()
                .withStatus(Status.ERROR)
                .withError(RaftError.Type.UNAVAILABLE)
                .build());
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<AppendResponse> onAppend(final InternalAppendRequest request) {
    logRequest(request);
    final var result =
        logResponse(
            AppendResponse.builder()
                .withStatus(Status.ERROR)
                .withError(RaftError.Type.UNAVAILABLE)
                .build());
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<PollResponse> onPoll(final PollRequest request) {
    logRequest(request);
    final var result =
        logResponse(
            PollResponse.builder()
                .withStatus(Status.ERROR)
                .withError(RaftError.Type.UNAVAILABLE)
                .build());
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<VoteResponse> onVote(final VoteRequest request) {
    logRequest(request);
    final var result =
        logResponse(
            VoteResponse.builder()
                .withStatus(Status.ERROR)
                .withError(RaftError.Type.UNAVAILABLE)
                .build());
    return CompletableFuture.completedFuture(result);
  }
}
