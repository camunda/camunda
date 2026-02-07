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
import io.atomix.raft.partition.RaftPartition;
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

  private final RaftMessageContext legacyContext;
  private final RaftMessageContext engineContext;
  private final RaftMessageContext sendContext;
  private final Serializer serializer;
  private final ClusterCommunicationService clusterCommunicator;
  private final RaftRequestMetrics metrics;
  private final Duration requestTimeout;
  private final Duration snapshotRequestTimeout;
  private final Duration configurationChangeTimeout;

  public RaftServerCommunicator(
      final RaftPartition partition,
      final Serializer serializer,
      final ClusterCommunicationService clusterCommunicator,
      final Duration requestTimeout,
      final Duration snapshotRequestTimeout,
      final Duration configurationChangeTimeout,
      final MeterRegistry meterRegistry) {
    final var config = partition.getPartitionConfig();

    final var legacyPrefix = partition.name();
    legacyContext = new RaftMessageContext(legacyPrefix);

    final var enginePrefix =
        RaftPartition.PARTITION_NAME_FORMAT.formatted(config.getEngineName(), partition.id().id());
    engineContext = new RaftMessageContext(enginePrefix);

    sendContext = config.isMultiEngineEnabled() ? engineContext : legacyContext;
    this.serializer = Preconditions.checkNotNull(serializer, "serializer cannot be null");
    this.clusterCommunicator =
        Preconditions.checkNotNull(clusterCommunicator, "clusterCommunicator cannot be null");
    this.requestTimeout = requestTimeout;
    this.snapshotRequestTimeout = snapshotRequestTimeout;
    this.configurationChangeTimeout = configurationChangeTimeout;
    metrics = new RaftRequestMetrics(legacyPrefix, meterRegistry);
  }

  @Override
  public CompletableFuture<ConfigureResponse> configure(
      final MemberId memberId, final ConfigureRequest request) {
    return sendAndReceive(sendContext.configureSubject, request, memberId);
  }

  @Override
  public CompletableFuture<ReconfigureResponse> reconfigure(
      final MemberId memberId, final ReconfigureRequest request) {

    return sendAndReceive(
        sendContext.reconfigureSubject, request, memberId, configurationChangeTimeout);
  }

  @Override
  public CompletableFuture<ForceConfigureResponse> forceConfigure(
      final MemberId memberId, final ForceConfigureRequest request) {
    return sendAndReceive(sendContext.forceConfigureSubject, request, memberId, requestTimeout);
  }

  @Override
  public CompletableFuture<JoinResponse> join(final MemberId memberId, final JoinRequest request) {
    return sendAndReceive(sendContext.joinSubject, request, memberId, configurationChangeTimeout);
  }

  @Override
  public CompletableFuture<LeaveResponse> leave(
      final MemberId memberId, final LeaveRequest request) {
    return sendAndReceive(sendContext.leaveSubject, request, memberId, configurationChangeTimeout);
  }

  @Override
  public CompletableFuture<InstallResponse> install(
      final MemberId memberId, final InstallRequest request) {
    return sendAndReceive(sendContext.installSubject, request, memberId, snapshotRequestTimeout);
  }

  @Override
  public CompletableFuture<TransferResponse> transfer(
      final MemberId memberId, final TransferRequest request) {
    return sendAndReceive(sendContext.transferSubject, request, memberId);
  }

  @Override
  public CompletableFuture<PollResponse> poll(final MemberId memberId, final PollRequest request) {
    return sendAndReceive(sendContext.pollSubject, request, memberId);
  }

  @Override
  public CompletableFuture<VoteResponse> vote(final MemberId memberId, final VoteRequest request) {
    return sendAndReceive(sendContext.voteSubject, request, memberId);
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final AppendRequest request) {
    return sendAndReceive(sendContext.appendV1subject, request, memberId);
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final VersionedAppendRequest request) {
    return sendAndReceive(sendContext.appendV2subject, request, memberId);
  }

  @Override
  public void registerTransferHandler(
      final Function<TransferRequest, CompletableFuture<TransferResponse>> handler) {
    clusterCommunicator.replyTo(
        new String[] {legacyContext.transferSubject, engineContext.transferSubject},
        serializer::decode,
        handler.<TransferRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterTransferHandler() {
    clusterCommunicator.unsubscribe(legacyContext.transferSubject);
    clusterCommunicator.unsubscribe(engineContext.transferSubject);
  }

  @Override
  public void registerConfigureHandler(
      final Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> handler) {
    clusterCommunicator.replyTo(
        new String[] {legacyContext.configureSubject, engineContext.configureSubject},
        serializer::decode,
        handler.<ConfigureRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterConfigureHandler() {
    clusterCommunicator.unsubscribe(engineContext.configureSubject);
    clusterCommunicator.unsubscribe(legacyContext.configureSubject);
  }

  @Override
  public void registerReconfigureHandler(
      final Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> handler) {
    clusterCommunicator.replyTo(
        new String[] {legacyContext.reconfigureSubject, engineContext.reconfigureSubject},
        serializer::decode,
        handler.<ReconfigureRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterReconfigureHandler() {
    clusterCommunicator.unsubscribe(engineContext.reconfigureSubject);
    clusterCommunicator.unsubscribe(legacyContext.reconfigureSubject);
  }

  @Override
  public void registerForceConfigureHandler(
      final Function<ForceConfigureRequest, CompletableFuture<ForceConfigureResponse>> handler) {
    clusterCommunicator.replyTo(
        new String[] {legacyContext.forceConfigureSubject, engineContext.forceConfigureSubject},
        serializer::decode,
        handler.<ForceConfigureRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterForceConfigureHandler() {
    clusterCommunicator.unsubscribe(engineContext.forceConfigureSubject);
    clusterCommunicator.unsubscribe(legacyContext.forceConfigureSubject);
  }

  @Override
  public void registerJoinHandler(
      final Function<JoinRequest, CompletableFuture<JoinResponse>> handler) {
    clusterCommunicator.replyTo(
        new String[] {legacyContext.joinSubject, engineContext.joinSubject},
        serializer::decode,
        handler.<JoinRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterJoinHandler() {
    clusterCommunicator.unsubscribe(engineContext.joinSubject);
    clusterCommunicator.unsubscribe(legacyContext.joinSubject);
  }

  @Override
  public void registerLeaveHandler(
      final Function<LeaveRequest, CompletableFuture<LeaveResponse>> handler) {
    clusterCommunicator.replyTo(
        new String[] {legacyContext.leaveSubject, engineContext.leaveSubject},
        serializer::decode,
        handler.<LeaveRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterLeaveHandler() {
    clusterCommunicator.unsubscribe(engineContext.leaveSubject);
    clusterCommunicator.unsubscribe(legacyContext.leaveSubject);
  }

  @Override
  public void registerInstallHandler(
      final Function<InstallRequest, CompletableFuture<InstallResponse>> handler) {
    clusterCommunicator.replyTo(
        new String[] {legacyContext.installSubject, engineContext.installSubject},
        serializer::decode,
        handler.<InstallRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterInstallHandler() {
    clusterCommunicator.unsubscribe(engineContext.installSubject);
    clusterCommunicator.unsubscribe(legacyContext.installSubject);
  }

  @Override
  public void registerPollHandler(
      final Function<PollRequest, CompletableFuture<PollResponse>> handler) {
    clusterCommunicator.replyTo(
        new String[] {legacyContext.pollSubject, engineContext.pollSubject},
        serializer::decode,
        handler.<PollRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterPollHandler() {
    clusterCommunicator.unsubscribe(engineContext.pollSubject);
    clusterCommunicator.unsubscribe(legacyContext.pollSubject);
  }

  @Override
  public void registerVoteHandler(
      final Function<VoteRequest, CompletableFuture<VoteResponse>> handler) {
    clusterCommunicator.replyTo(
        new String[] {legacyContext.voteSubject, engineContext.voteSubject},
        serializer::decode,
        handler.<VoteRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterVoteHandler() {
    clusterCommunicator.unsubscribe(engineContext.voteSubject);
    clusterCommunicator.unsubscribe(legacyContext.voteSubject);
  }

  @Override
  public void registerAppendV1Handler(
      final Function<AppendRequest, CompletableFuture<AppendResponse>> handler) {
    clusterCommunicator.replyTo(
        new String[] {legacyContext.appendV1subject, engineContext.appendV1subject},
        serializer::decode,
        handler.<AppendRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void registerAppendV2Handler(
      final Function<VersionedAppendRequest, CompletableFuture<AppendResponse>> handler) {
    clusterCommunicator.replyTo(
        new String[] {legacyContext.appendV2subject, engineContext.appendV2subject},
        serializer::decode,
        handler.<VersionedAppendRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterAppendHandler() {
    clusterCommunicator.unsubscribe(engineContext.appendV1subject);
    clusterCommunicator.unsubscribe(legacyContext.appendV1subject);
    clusterCommunicator.unsubscribe(engineContext.appendV2subject);
    clusterCommunicator.unsubscribe(legacyContext.appendV2subject);
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
