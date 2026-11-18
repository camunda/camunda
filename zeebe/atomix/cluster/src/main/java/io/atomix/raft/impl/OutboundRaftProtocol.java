/*
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
package io.atomix.raft.impl;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftError;
import io.atomix.raft.RaftException;
import io.atomix.raft.protocol.AbstractRaftResponse;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.ForceConfigureRequest;
import io.atomix.raft.protocol.ForceConfigureResponse;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.JoinResponse;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.LeaveResponse;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.RaftRequest;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.RaftServerProtocol;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.TimeoutNowRequest;
import io.atomix.raft.protocol.TimeoutNowResponse;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.protocol.TransferResponse;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Decorates the protocol that the local member uses to send requests, so that fencing is applied in
 * one place instead of at every request and response handler:
 *
 * <ul>
 *   <li>every request that the local member sends on its own behalf carries its incarnation;
 *   <li>a {@link RaftError.Type#FENCED} rejection of such a request tells the local member that it
 *       is fenced; the rejection of a forwarded request is only passed back to its sender;
 *   <li>a response from a fenced member fails like an unreachable member, so that its term is never
 *       taken over;
 *   <li>a response from another incarnation than the one of the configuration fails the same way
 *       (R17).
 * </ul>
 *
 * <p>Requests are sent on the Raft thread; responses are checked on the thread that completes them.
 */
final class OutboundRaftProtocol implements RaftServerProtocol {

  private final RaftServerProtocol delegate;
  private final RaftContext raft;

  OutboundRaftProtocol(final RaftServerProtocol delegate, final RaftContext raft) {
    this.delegate = delegate;
    this.raft = raft;
  }

  /**
   * Stamps the request, sends it and checks the response.
   *
   * @param send sends the stamped request to the target
   */
  private <Q extends RaftRequest, R> CompletableFuture<R> send(
      final MemberId target, final Q request, final Function<Q, CompletableFuture<R>> send) {
    final var localMember = raft.getCluster().getLocalMember();
    // A forwarded request keeps the incarnation of its original sender.
    final boolean ownRequest = localMember.memberId().equals(request.from());
    if (ownRequest) {
      request.setSenderIncarnation(localMember.getIncarnation());
    }
    return send.apply(request).thenApply(response -> check(target, ownRequest, response));
  }

  private <R> R check(final MemberId target, final boolean ownRequest, final R response) {
    if (!(response instanceof final AbstractRaftResponse raftResponse)) {
      return response;
    }
    if (raftResponse.status() == Status.ERROR
        && raftResponse.error() != null
        && raftResponse.error().type() == RaftError.Type.FENCED) {
      if (ownRequest) {
        raft.getThreadContext()
            .execute(
                () ->
                    raft.onFenced(
                        "%s rejected a request: %s"
                            .formatted(target, raftResponse.error().message())));
      }
      return response;
    }
    if (raft.getCluster().isFenced(target, raftResponse.senderIncarnation())) {
      throw new CompletionException(
          new RaftException.ProtocolException(
              "Ignoring the response of %s, its incarnation %d is fenced",
              target, raftResponse.senderIncarnation()));
    }
    // A response counts for a member only if it comes from the incarnation that the configuration
    // has for the member (R17): a new incarnation of a broker lost the log of the old one, thus its
    // answers must not count for the old one, also not for the quorum or the match index.
    final var configuration = raft.getCluster().getConfiguration();
    if (ownRequest && configuration != null) {
      final var expected = configuration.incarnationOf(target);
      if (expected.isPresent() && expected.getAsLong() != raftResponse.senderIncarnation()) {
        throw new CompletionException(
            new RaftException.ProtocolException(
                "Ignoring the response of %s, its incarnation %d is not the incarnation %d of the configuration",
                target, raftResponse.senderIncarnation(), expected.getAsLong()));
      }
    }
    return response;
  }

  @Override
  public CompletableFuture<ConfigureResponse> configure(
      final MemberId memberId, final ConfigureRequest request) {
    return send(memberId, request, r -> delegate.configure(memberId, r));
  }

  @Override
  public CompletableFuture<ReconfigureResponse> reconfigure(
      final MemberId memberId, final ReconfigureRequest request) {
    return send(memberId, request, r -> delegate.reconfigure(memberId, r));
  }

  @Override
  public CompletableFuture<ForceConfigureResponse> forceConfigure(
      final MemberId memberId, final ForceConfigureRequest request) {
    return send(memberId, request, r -> delegate.forceConfigure(memberId, r));
  }

  @Override
  public CompletableFuture<JoinResponse> join(final MemberId memberId, final JoinRequest request) {
    return send(memberId, request, r -> delegate.join(memberId, r));
  }

  @Override
  public CompletableFuture<LeaveResponse> leave(
      final MemberId memberId, final LeaveRequest request) {
    return send(memberId, request, r -> delegate.leave(memberId, r));
  }

  @Override
  public CompletableFuture<InstallResponse> install(
      final MemberId memberId, final InstallRequest request) {
    return send(memberId, request, r -> delegate.install(memberId, r));
  }

  @Override
  public CompletableFuture<TransferResponse> transfer(
      final MemberId memberId, final TransferRequest request) {
    return send(memberId, request, r -> delegate.transfer(memberId, r));
  }

  @Override
  public CompletableFuture<TimeoutNowResponse> timeoutNow(
      final MemberId memberId, final TimeoutNowRequest request) {
    return send(memberId, request, r -> delegate.timeoutNow(memberId, r));
  }

  @Override
  public CompletableFuture<LeadershipTransferInitiateResponse> leadershipTransferInitiate(
      final MemberId memberId, final LeadershipTransferInitiateRequest request) {
    return send(memberId, request, r -> delegate.leadershipTransferInitiate(memberId, r));
  }

  @Override
  public CompletableFuture<LeadershipTransferResultResponse> leadershipTransferResult(
      final MemberId memberId, final LeadershipTransferResultRequest request) {
    return send(memberId, request, r -> delegate.leadershipTransferResult(memberId, r));
  }

  @Override
  public CompletableFuture<PollResponse> poll(final MemberId memberId, final PollRequest request) {
    return send(memberId, request, r -> delegate.poll(memberId, r));
  }

  @Override
  public CompletableFuture<VoteResponse> vote(final MemberId memberId, final VoteRequest request) {
    return send(memberId, request, r -> delegate.vote(memberId, r));
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final AppendRequest request) {
    return send(memberId, request, r -> delegate.append(memberId, r));
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final VersionedAppendRequest request) {
    return send(memberId, request, r -> delegate.append(memberId, r));
  }

  @Override
  public void registerTransferHandler(
      final Function<TransferRequest, CompletableFuture<TransferResponse>> handler) {
    delegate.registerTransferHandler(handler);
  }

  @Override
  public void unregisterTransferHandler() {
    delegate.unregisterTransferHandler();
  }

  @Override
  public void registerTimeoutNowHandler(
      final Function<TimeoutNowRequest, CompletableFuture<TimeoutNowResponse>> handler) {
    delegate.registerTimeoutNowHandler(handler);
  }

  @Override
  public void unregisterTimeoutNowHandler() {
    delegate.unregisterTimeoutNowHandler();
  }

  @Override
  public void registerLeadershipTransferInitiateHandler(
      final Function<
              LeadershipTransferInitiateRequest,
              CompletableFuture<LeadershipTransferInitiateResponse>>
          handler) {
    delegate.registerLeadershipTransferInitiateHandler(handler);
  }

  @Override
  public void unregisterLeadershipTransferInitiateHandler() {
    delegate.unregisterLeadershipTransferInitiateHandler();
  }

  @Override
  public void registerLeadershipTransferResultHandler(
      final Function<
              LeadershipTransferResultRequest, CompletableFuture<LeadershipTransferResultResponse>>
          handler) {
    delegate.registerLeadershipTransferResultHandler(handler);
  }

  @Override
  public void unregisterLeadershipTransferResultHandler() {
    delegate.unregisterLeadershipTransferResultHandler();
  }

  @Override
  public void registerConfigureHandler(
      final Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> handler) {
    delegate.registerConfigureHandler(handler);
  }

  @Override
  public void unregisterConfigureHandler() {
    delegate.unregisterConfigureHandler();
  }

  @Override
  public void registerReconfigureHandler(
      final Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> handler) {
    delegate.registerReconfigureHandler(handler);
  }

  @Override
  public void unregisterReconfigureHandler() {
    delegate.unregisterReconfigureHandler();
  }

  @Override
  public void registerForceConfigureHandler(
      final Function<ForceConfigureRequest, CompletableFuture<ForceConfigureResponse>> handler) {
    delegate.registerForceConfigureHandler(handler);
  }

  @Override
  public void unregisterForceConfigureHandler() {
    delegate.unregisterForceConfigureHandler();
  }

  @Override
  public void registerJoinHandler(
      final Function<JoinRequest, CompletableFuture<JoinResponse>> handler) {
    delegate.registerJoinHandler(handler);
  }

  @Override
  public void unregisterJoinHandler() {
    delegate.unregisterJoinHandler();
  }

  @Override
  public void registerLeaveHandler(
      final Function<LeaveRequest, CompletableFuture<LeaveResponse>> handler) {
    delegate.registerLeaveHandler(handler);
  }

  @Override
  public void unregisterLeaveHandler() {
    delegate.unregisterLeaveHandler();
  }

  @Override
  public void registerInstallHandler(
      final Function<InstallRequest, CompletableFuture<InstallResponse>> handler) {
    delegate.registerInstallHandler(handler);
  }

  @Override
  public void unregisterInstallHandler() {
    delegate.unregisterInstallHandler();
  }

  @Override
  public void registerPollHandler(
      final Function<PollRequest, CompletableFuture<PollResponse>> handler) {
    delegate.registerPollHandler(handler);
  }

  @Override
  public void unregisterPollHandler() {
    delegate.unregisterPollHandler();
  }

  @Override
  public void registerVoteHandler(
      final Function<VoteRequest, CompletableFuture<VoteResponse>> handler) {
    delegate.registerVoteHandler(handler);
  }

  @Override
  public void unregisterVoteHandler() {
    delegate.unregisterVoteHandler();
  }

  @Override
  public void registerAppendV1Handler(
      final Function<AppendRequest, CompletableFuture<AppendResponse>> handler) {
    delegate.registerAppendV1Handler(handler);
  }

  @Override
  public void registerAppendV2Handler(
      final Function<VersionedAppendRequest, CompletableFuture<AppendResponse>> handler) {
    delegate.registerAppendV2Handler(handler);
  }

  @Override
  public void unregisterAppendHandler() {
    delegate.unregisterAppendHandler();
  }
}
