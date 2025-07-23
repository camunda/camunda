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
import io.atomix.raft.protocol.RaftServerProtocol;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.protocol.TransferResponse;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.utils.serializer.Serializer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Raft server protocol that uses a {@link ClusterCommunicationService}. */
public class RaftServerCommunicator implements RaftServerProtocol {

  private final RaftMessageContext context;
  private final Serializer serializer;
  private final ClusterCommunicationService clusterCommunicator;
  private final RaftRequestMetrics metrics;
  private final Duration requestTimeout;
  private final Duration snapshotRequestTimeout;
  private final Duration configurationChangeTimeout;

  public RaftServerCommunicator(
      final String prefix,
      final Serializer serializer,
      final ClusterCommunicationService clusterCommunicator,
      final Duration requestTimeout,
      final Duration snapshotRequestTimeout,
      final Duration configurationChangeTimeout,
      final MeterRegistry meterRegistry) {
    context = new RaftMessageContext(prefix);
    this.serializer = Preconditions.checkNotNull(serializer, "serializer cannot be null");
    this.clusterCommunicator =
        Preconditions.checkNotNull(clusterCommunicator, "clusterCommunicator cannot be null");
    this.requestTimeout = requestTimeout;
    this.snapshotRequestTimeout = snapshotRequestTimeout;
    this.configurationChangeTimeout = configurationChangeTimeout;
    metrics = new RaftRequestMetrics(prefix, meterRegistry);
  }

  @Override
  public CompletableFuture<ConfigureResponse> configure(
      final MemberId memberId, final ConfigureRequest request) {
    return sendAndReceive(context.configureSubject, request, memberId);
  }

  @Override
  public CompletableFuture<ReconfigureResponse> reconfigure(
      final MemberId memberId, final ReconfigureRequest request) {
    return sendAndReceive(
        context.reconfigureSubject, request, memberId, configurationChangeTimeout);
  }

  @Override
  public CompletableFuture<ForceConfigureResponse> forceConfigure(
      final MemberId memberId, final ForceConfigureRequest request) {
    return sendAndReceive(context.forceConfigureSubject, request, memberId, requestTimeout);
  }

  @Override
  public CompletableFuture<JoinResponse> join(final MemberId memberId, final JoinRequest request) {
    return sendAndReceive(context.joinSubject, request, memberId, configurationChangeTimeout);
  }

  @Override
  public CompletableFuture<LeaveResponse> leave(
      final MemberId memberId, final LeaveRequest request) {
    return sendAndReceive(context.leaveSubject, request, memberId, configurationChangeTimeout);
  }

  @Override
  public CompletableFuture<InstallResponse> install(
      final MemberId memberId, final InstallRequest request) {
    return sendAndReceive(context.installSubject, request, memberId, snapshotRequestTimeout, true);
  }

  @Override
  public CompletableFuture<TransferResponse> transfer(
      final MemberId memberId, final TransferRequest request) {
    return sendAndReceive(context.transferSubject, request, memberId);
  }

  @Override
  public CompletableFuture<PollResponse> poll(final MemberId memberId, final PollRequest request) {
    return sendAndReceive(context.pollSubject, request, memberId);
  }

  @Override
  public CompletableFuture<VoteResponse> vote(final MemberId memberId, final VoteRequest request) {
    return sendAndReceive(context.voteSubject, request, memberId);
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final AppendRequest request) {
    return sendAndReceive(context.appendV1subject, request, memberId);
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final VersionedAppendRequest request) {
    return sendAndReceive(context.appendV2subject, request, memberId);
  }

  @Override
  public void registerTransferHandler(
      final Function<TransferRequest, CompletableFuture<TransferResponse>> handler) {
    clusterCommunicator.replyTo(
        context.transferSubject,
        serializer::decode,
        handler.<TransferRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterTransferHandler() {
    clusterCommunicator.unsubscribe(context.transferSubject);
  }

  @Override
  public void registerConfigureHandler(
      final Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> handler) {
    clusterCommunicator.replyTo(
        context.configureSubject,
        serializer::decode,
        handler.<ConfigureRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterConfigureHandler() {
    clusterCommunicator.unsubscribe(context.configureSubject);
  }

  @Override
  public void registerReconfigureHandler(
      final Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> handler) {
    clusterCommunicator.replyTo(
        context.reconfigureSubject,
        serializer::decode,
        handler.<ReconfigureRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterReconfigureHandler() {
    clusterCommunicator.unsubscribe(context.reconfigureSubject);
  }

  @Override
  public void registerForceConfigureHandler(
      final Function<ForceConfigureRequest, CompletableFuture<ForceConfigureResponse>> handler) {
    clusterCommunicator.replyTo(
        context.forceConfigureSubject,
        serializer::decode,
        handler.<ForceConfigureRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterForceConfigureHandler() {
    clusterCommunicator.unsubscribe(context.forceConfigureSubject);
  }

  @Override
  public void registerJoinHandler(
      final Function<JoinRequest, CompletableFuture<JoinResponse>> handler) {
    clusterCommunicator.replyTo(
        context.joinSubject,
        serializer::decode,
        handler.<JoinRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterJoinHandler() {
    clusterCommunicator.unsubscribe(context.joinSubject);
  }

  @Override
  public void registerLeaveHandler(
      final Function<LeaveRequest, CompletableFuture<LeaveResponse>> handler) {
    clusterCommunicator.replyTo(
        context.leaveSubject,
        serializer::decode,
        handler.<LeaveRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterLeaveHandler() {
    clusterCommunicator.unsubscribe(context.leaveSubject);
  }

  @Override
  public void registerInstallHandler(
      final Function<InstallRequest, CompletableFuture<InstallResponse>> handler) {
    clusterCommunicator.replyTo(
        context.installSubject,
        serializer::decode,
        handler.<InstallRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterInstallHandler() {
    clusterCommunicator.unsubscribe(context.installSubject);
  }

  @Override
  public void registerPollHandler(
      final Function<PollRequest, CompletableFuture<PollResponse>> handler) {
    clusterCommunicator.replyTo(
        context.pollSubject,
        serializer::decode,
        handler.<PollRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterPollHandler() {
    clusterCommunicator.unsubscribe(context.pollSubject);
  }

  @Override
  public void registerVoteHandler(
      final Function<VoteRequest, CompletableFuture<VoteResponse>> handler) {
    clusterCommunicator.replyTo(
        context.voteSubject,
        serializer::decode,
        handler.<VoteRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterVoteHandler() {
    clusterCommunicator.unsubscribe(context.voteSubject);
  }

  @Override
  public void registerAppendV1Handler(
      final Function<AppendRequest, CompletableFuture<AppendResponse>> handler) {
    clusterCommunicator.replyTo(
        context.appendV1subject,
        serializer::decode,
        handler.<AppendRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void registerAppendV2Handler(
      final Function<VersionedAppendRequest, CompletableFuture<AppendResponse>> handler) {
    clusterCommunicator.replyTo(
        context.appendV2subject,
        serializer::decode,
        handler.<VersionedAppendRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterAppendHandler() {
    clusterCommunicator.unsubscribe(context.appendV1subject);
    clusterCommunicator.unsubscribe(context.appendV2subject);
  }

  private <T, U> CompletableFuture<U> sendAndReceive(
      final String subject, final T request, final MemberId memberId) {
    return sendAndReceive(subject, request, memberId, requestTimeout);
  }

  private <T, U> CompletableFuture<U> sendAndReceive(
      final String subject, final T request, final MemberId memberId, final Duration timeout) {
    return sendAndReceive(subject, request, memberId, timeout, false);
  }

  private <T, U> CompletableFuture<U> sendAndReceive(
      final String subject,
      final T request,
      final MemberId memberId,
      final Duration timeout,
      final boolean dedicatedConnection) {
    metrics.sendMessage(memberId.id(), request.getClass().getSimpleName());
    return clusterCommunicator.send(
        subject,
        request,
        serializer::encode,
        serializer::decode,
        memberId,
        timeout,
        dedicatedConnection);
  }

  private <T extends RaftMessage> T recordReceivedMetrics(final T m) {
    metrics.receivedMessage(m.getClass().getSimpleName());
    return m;
  }
}
