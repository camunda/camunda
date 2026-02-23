/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.raft.partition.impl;

import com.google.common.base.Preconditions;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.raft.metrics.RaftRequestMetrics;
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
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.LeaveResponse;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.RaftMessage;
import io.atomix.raft.protocol.RaftRequest;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.RaftServerProtocol;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.protocol.TransferResponse;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.utils.serializer.Serializer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Raft server protocol that uses a {@link ClusterCommunicationService}. */
public class RaftServerCommunicator implements RaftServerProtocol {

  private final RaftMessageContext sendingSubject;
  private final List<RaftMessageContext> receivingSubjects;
  private final Serializer serializer;
  private final ClusterCommunicationService clusterCommunicator;
  private final RaftRequestMetrics metrics;
  private final Duration requestTimeout;
  private final Duration snapshotRequestTimeout;
  private final Duration configurationChangeTimeout;

  public RaftServerCommunicator(
      final RaftMessageContext sendingSubject,
      final List<RaftMessageContext> receivingSubjects,
      final Serializer serializer,
      final ClusterCommunicationService clusterCommunicator,
      final Duration requestTimeout,
      final Duration snapshotRequestTimeout,
      final Duration configurationChangeTimeout,
      final RaftRequestMetrics metrics) {
    this.sendingSubject = sendingSubject;
    this.receivingSubjects = receivingSubjects;
    this.serializer = Preconditions.checkNotNull(serializer, "serializer cannot be null");
    this.clusterCommunicator =
        Preconditions.checkNotNull(clusterCommunicator, "clusterCommunicator cannot be null");
    this.requestTimeout = requestTimeout;
    this.snapshotRequestTimeout = snapshotRequestTimeout;
    this.configurationChangeTimeout = configurationChangeTimeout;
    this.metrics = metrics;
  }

  @Override
  public CompletableFuture<ConfigureResponse> configure(
      final MemberId memberId, final ConfigureRequest request) {
    return sendAndReceive(sendingSubject.getConfigureSubject(), request, memberId);
  }

  @Override
  public CompletableFuture<ReconfigureResponse> reconfigure(
      final MemberId memberId, final ReconfigureRequest request) {
    return sendAndReceive(
        sendingSubject.getReconfigureSubject(), request, memberId, configurationChangeTimeout);
  }

  @Override
  public CompletableFuture<ForceConfigureResponse> forceConfigure(
      final MemberId memberId, final ForceConfigureRequest request) {
    return sendAndReceive(
        sendingSubject.getForceConfigureSubject(), request, memberId, requestTimeout);
  }

  @Override
  public CompletableFuture<JoinResponse> join(final MemberId memberId, final JoinRequest request) {
    return sendAndReceive(
        sendingSubject.getJoinSubject(), request, memberId, configurationChangeTimeout);
  }

  @Override
  public CompletableFuture<LeaveResponse> leave(
      final MemberId memberId, final LeaveRequest request) {
    return sendAndReceive(
        sendingSubject.getLeaveSubject(), request, memberId, configurationChangeTimeout);
  }

  @Override
  public CompletableFuture<InstallResponse> install(
      final MemberId memberId, final InstallRequest request) {
    return sendAndReceive(
        sendingSubject.getInstallSubject(), request, memberId, snapshotRequestTimeout);
  }

  @Override
  public CompletableFuture<TransferResponse> transfer(
      final MemberId memberId, final TransferRequest request) {
    return sendAndReceive(sendingSubject.getTransferSubject(), request, memberId);
  }

  @Override
  public CompletableFuture<PollResponse> poll(final MemberId memberId, final PollRequest request) {
    return sendAndReceive(sendingSubject.getPollSubject(), request, memberId);
  }

  @Override
  public CompletableFuture<VoteResponse> vote(final MemberId memberId, final VoteRequest request) {
    return sendAndReceive(sendingSubject.getVoteSubject(), request, memberId);
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final AppendRequest request) {
    return sendAndReceive(sendingSubject.getAppendV1Subject(), request, memberId);
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final VersionedAppendRequest request) {
    return sendAndReceive(sendingSubject.getAppendV2Subject(), request, memberId);
  }

  @Override
  public void registerTransferHandler(
      final Function<TransferRequest, CompletableFuture<TransferResponse>> handler) {
    registerHandler(RaftMessageContext::getTransferSubject, handler);
  }

  @Override
  public void unregisterTransferHandler() {
    unregisterHandler(RaftMessageContext::getTransferSubject);
  }

  @Override
  public void registerConfigureHandler(
      final Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> handler) {
    registerHandler(RaftMessageContext::getConfigureSubject, handler);
  }

  @Override
  public void unregisterConfigureHandler() {
    unregisterHandler(RaftMessageContext::getConfigureSubject);
  }

  @Override
  public void registerReconfigureHandler(
      final Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> handler) {
    registerHandler(RaftMessageContext::getReconfigureSubject, handler);
  }

  @Override
  public void unregisterReconfigureHandler() {
    unregisterHandler(RaftMessageContext::getReconfigureSubject);
  }

  @Override
  public void registerForceConfigureHandler(
      final Function<ForceConfigureRequest, CompletableFuture<ForceConfigureResponse>> handler) {
    registerHandler(RaftMessageContext::getForceConfigureSubject, handler);
  }

  @Override
  public void unregisterForceConfigureHandler() {
    unregisterHandler(RaftMessageContext::getForceConfigureSubject);
  }

  @Override
  public void registerJoinHandler(
      final Function<JoinRequest, CompletableFuture<JoinResponse>> handler) {
    registerHandler(RaftMessageContext::getJoinSubject, handler);
  }

  @Override
  public void unregisterJoinHandler() {
    unregisterHandler(RaftMessageContext::getJoinSubject);
  }

  @Override
  public void registerLeaveHandler(
      final Function<LeaveRequest, CompletableFuture<LeaveResponse>> handler) {
    registerHandler(RaftMessageContext::getLeaveSubject, handler);
  }

  @Override
  public void unregisterLeaveHandler() {
    unregisterHandler(RaftMessageContext::getLeaveSubject);
  }

  @Override
  public void registerInstallHandler(
      final Function<InstallRequest, CompletableFuture<InstallResponse>> handler) {
    registerHandler(RaftMessageContext::getInstallSubject, handler);
  }

  @Override
  public void unregisterInstallHandler() {
    unregisterHandler(RaftMessageContext::getInstallSubject);
  }

  @Override
  public void registerPollHandler(
      final Function<PollRequest, CompletableFuture<PollResponse>> handler) {
    registerHandler(RaftMessageContext::getPollSubject, handler);
  }

  @Override
  public void unregisterPollHandler() {
    unregisterHandler(RaftMessageContext::getPollSubject);
  }

  @Override
  public void registerVoteHandler(
      final Function<VoteRequest, CompletableFuture<VoteResponse>> handler) {
    registerHandler(RaftMessageContext::getVoteSubject, handler);
  }

  @Override
  public void unregisterVoteHandler() {
    unregisterHandler(RaftMessageContext::getVoteSubject);
  }

  @Override
  public void registerAppendV1Handler(
      final Function<AppendRequest, CompletableFuture<AppendResponse>> handler) {
    registerHandler(RaftMessageContext::getAppendV1Subject, handler);
  }

  @Override
  public void registerAppendV2Handler(
      final Function<VersionedAppendRequest, CompletableFuture<AppendResponse>> handler) {
    registerHandler(RaftMessageContext::getAppendV2Subject, handler);
  }

  @Override
  public void unregisterAppendHandler() {
    unregisterHandler(RaftMessageContext::getAppendV1Subject);
    unregisterHandler(RaftMessageContext::getAppendV2Subject);
  }

  private <M extends RaftRequest, R extends RaftResponse> void registerHandler(
      final Function<RaftMessageContext, String> subjectSupplier,
      final Function<M, CompletableFuture<R>> handler) {
    receivingSubjects.stream().map(subjectSupplier).forEach(subject -> subscribe(subject, handler));
  }

  private <M extends RaftRequest, R extends RaftResponse> void subscribe(
      final String subject, final Function<M, CompletableFuture<R>> handler) {
    clusterCommunicator.replyTo(
        subject,
        serializer::decode,
        handler.<M>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  private void unregisterHandler(final Function<RaftMessageContext, String> subjectSupplier) {
    receivingSubjects.stream().map(subjectSupplier).forEach(this::unsubscribe);
  }

  private void unsubscribe(final String subject) {
    clusterCommunicator.unsubscribe(subject);
  }

  private <T, U> CompletableFuture<U> sendAndReceive(
      final String subject, final T request, final MemberId memberId) {
    return sendAndReceive(subject, request, memberId, requestTimeout);
  }

  private <T, U> CompletableFuture<U> sendAndReceive(
      final String subject, final T request, final MemberId memberId, final Duration timeout) {
    metrics.sendMessage(memberId.id(), request.getClass().getSimpleName());
    return clusterCommunicator.send(
        subject, request, serializer::encode, serializer::decode, memberId, timeout);
  }

  private <T extends RaftMessage> T recordReceivedMetrics(final T m) {
    metrics.receivedMessage(m.getClass().getSimpleName());
    return m;
  }
}
