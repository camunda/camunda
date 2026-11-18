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
import io.atomix.raft.cluster.RaftMember;
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
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.LeaveResponse;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.TimeoutNowRequest;
import io.atomix.raft.protocol.TimeoutNowResponse;
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

    // The leader sends a committed configuration that fences this member instead of entries (the
    // "fenced notice"). Act on it before applying it: this member's log is no longer valid.
    final var localMember = raft.getCluster().getLocalMember();
    if (request.committed()
        && request.fenced().stream()
            .anyMatch(f -> f.matches(localMember.memberId(), localMember.getIncarnation()))) {
      raft.onFenced(
          "the leader %s sent a committed configuration that fences it"
              .formatted(request.leader()));
      return CompletableFuture.completedFuture(
          logResponse(ConfigureResponse.builder().withStatus(RaftResponse.Status.OK).build()));
    }

    // Store the term that identifies the configuration, not the term it was disseminated under, so
    // that Configuration#term() means the same thing here as on the member that appended the entry.
    // A leader that predates the configurationTerm field sends 0; fall back to its current term,
    // which is the ambiguous value this member would have stored before.
    final var configurationTerm =
        request.configurationTerm() > 0 ? request.configurationTerm() : request.term();
    final Configuration configuration =
        new Configuration(
            request.index(),
            configurationTerm,
            request.timestamp(),
            request.newMembers(),
            request.oldMembers() != null ? request.oldMembers() : List.of(),
            false,
            request.fenced(),
            request.membershipVersion());

    // A member that started without a configuration (R16) has no data: it accepts only a
    // configuration in which it is passive with its own incarnation, so that it never votes before
    // the leader promotes it after it has caught up.
    final var withoutConfiguration = raft.getCluster().getConfiguration() == null;
    if (withoutConfiguration) {
      final var refusal = refusalWithoutConfiguration(configuration);
      if (refusal != null) {
        log.debug("Rejecting {}: {}", request, refusal);
        return CompletableFuture.completedFuture(
            logResponse(
                ConfigureResponse.builder()
                    .withStatus(Status.ERROR)
                    .withError(Type.ILLEGAL_MEMBER_STATE, refusal)
                    .build()));
      }
    }

    // Configure the cluster membership. This will cause this server to transition to the
    // appropriate state if its type has changed.
    raft.getCluster().configure(configuration);

    if (withoutConfiguration) {
      // Stored at once, so that a restart starts with it (row 1 of the start table) and stays
      // passive, instead of starting without data again with a new incarnation. It can only make
      // this member passive, thus it is safe even if it is not committed.
      raft.getMetaStore().storeConfiguration(raft.getCluster().getConfiguration());
    }

    // If the configuration is already committed, commit it to disk.
    // Check against the actual cluster Configuration rather than the received configuration in
    // case the received configuration was an older configuration that was not applied.
    if (raft.getCommitIndex() >= raft.getCluster().getConfiguration().index()) {
      raft.getCluster().commitCurrentConfiguration();
    }

    return CompletableFuture.completedFuture(
        logResponse(ConfigureResponse.builder().withStatus(RaftResponse.Status.OK).build()));
  }

  /**
   * @return null if a member without a configuration may accept the configuration, else the reason
   *     why not
   */
  private String refusalWithoutConfiguration(final Configuration configuration) {
    final var local = raft.getCluster().getLocalMember();
    if (configuration.force()) {
      return "a member without data does not accept a forced configuration";
    }
    final var newMember =
        configuration.newMembers().stream()
            .filter(member -> member.memberId().equals(local.memberId()))
            .findAny();
    if (newMember.isEmpty()
        || newMember.get().getType() != RaftMember.Type.PASSIVE
        || newMember.get().getIncarnation() != local.getIncarnation()) {
      return "a member without data accepts only a configuration in which it is %s with its incarnation %d, but got %s"
          .formatted(RaftMember.Type.PASSIVE, local.getIncarnation(), newMember);
    }
    final var otherIncarnation =
        configuration.oldMembers().stream()
            .filter(member -> member.memberId().equals(local.memberId()))
            .filter(
                member ->
                    member.getIncarnation() != local.getIncarnation()
                        || member.getType() != RaftMember.Type.PASSIVE)
            .findAny();
    if (otherIncarnation.isPresent()) {
      return "the old members of the joint configuration have %s, a member without data waits for the end of the joint phase"
          .formatted(otherIncarnation.get());
    }
    return null;
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
  public CompletableFuture<TimeoutNowResponse> onTimeoutNow(final TimeoutNowRequest request) {
    logRequest(request);
    final var result =
        logResponse(
            TimeoutNowResponse.builder()
                .withStatus(Status.ERROR)
                .withError(RaftError.Type.UNAVAILABLE)
                .build());
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public CompletableFuture<LeadershipTransferInitiateResponse> onLeadershipTransferInitiate(
      final LeadershipTransferInitiateRequest request) {
    logRequest(request);
    final var result =
        logResponse(
            LeadershipTransferInitiateResponse.builder()
                .withStatus(Status.ERROR)
                .withError(RaftError.Type.ILLEGAL_MEMBER_STATE)
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
