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
import io.atomix.primitive.session.SessionId;
import io.atomix.raft.metrics.RaftRequestMetrics;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.CloseSessionRequest;
import io.atomix.raft.protocol.CloseSessionResponse;
import io.atomix.raft.protocol.CommandRequest;
import io.atomix.raft.protocol.CommandResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.HeartbeatRequest;
import io.atomix.raft.protocol.HeartbeatResponse;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.JoinResponse;
import io.atomix.raft.protocol.KeepAliveRequest;
import io.atomix.raft.protocol.KeepAliveResponse;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.LeaveResponse;
import io.atomix.raft.protocol.MetadataRequest;
import io.atomix.raft.protocol.MetadataResponse;
import io.atomix.raft.protocol.OpenSessionRequest;
import io.atomix.raft.protocol.OpenSessionResponse;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.PublishRequest;
import io.atomix.raft.protocol.QueryRequest;
import io.atomix.raft.protocol.QueryResponse;
import io.atomix.raft.protocol.RaftMessage;
import io.atomix.raft.protocol.RaftServerProtocol;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.ResetRequest;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.protocol.TransferResponse;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.utils.serializer.Serializer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** Raft server protocol that uses a {@link ClusterCommunicationService}. */
public class RaftServerCommunicator implements RaftServerProtocol {

  private final RaftMessageContext context;
  private final Serializer serializer;
  private final ClusterCommunicationService clusterCommunicator;
  private final String partitionName;
  private final RaftRequestMetrics metrics;

  public RaftServerCommunicator(
      final Serializer serializer, final ClusterCommunicationService clusterCommunicator) {
    this(null, serializer, clusterCommunicator);
  }

  public RaftServerCommunicator(
      final String prefix,
      final Serializer serializer,
      final ClusterCommunicationService clusterCommunicator) {
    this.context = new RaftMessageContext(prefix);
    this.partitionName = prefix;
    this.serializer = Preconditions.checkNotNull(serializer, "serializer cannot be null");
    this.clusterCommunicator =
        Preconditions.checkNotNull(clusterCommunicator, "clusterCommunicator cannot be null");
    this.metrics = new RaftRequestMetrics(partitionName);
  }

  @Override
  public CompletableFuture<OpenSessionResponse> openSession(
      final MemberId memberId, final OpenSessionRequest request) {
    return sendAndReceive(context.openSessionSubject, request, memberId);
  }

  @Override
  public CompletableFuture<CloseSessionResponse> closeSession(
      final MemberId memberId, final CloseSessionRequest request) {
    return sendAndReceive(context.closeSessionSubject, request, memberId);
  }

  @Override
  public CompletableFuture<KeepAliveResponse> keepAlive(
      final MemberId memberId, final KeepAliveRequest request) {
    return sendAndReceive(context.keepAliveSubject, request, memberId);
  }

  @Override
  public CompletableFuture<QueryResponse> query(
      final MemberId memberId, final QueryRequest request) {
    return sendAndReceive(context.querySubject, request, memberId);
  }

  @Override
  public CompletableFuture<CommandResponse> command(
      final MemberId memberId, final CommandRequest request) {
    return sendAndReceive(context.commandSubject, request, memberId);
  }

  @Override
  public CompletableFuture<MetadataResponse> metadata(
      final MemberId memberId, final MetadataRequest request) {
    return sendAndReceive(context.metadataSubject, request, memberId);
  }

  @Override
  public CompletableFuture<JoinResponse> join(final MemberId memberId, final JoinRequest request) {
    return sendAndReceive(context.joinSubject, request, memberId);
  }

  @Override
  public CompletableFuture<LeaveResponse> leave(
      final MemberId memberId, final LeaveRequest request) {
    return sendAndReceive(context.leaveSubject, request, memberId);
  }

  @Override
  public CompletableFuture<ConfigureResponse> configure(
      final MemberId memberId, final ConfigureRequest request) {
    return sendAndReceive(context.configureSubject, request, memberId);
  }

  @Override
  public CompletableFuture<ReconfigureResponse> reconfigure(
      final MemberId memberId, final ReconfigureRequest request) {
    return sendAndReceive(context.reconfigureSubject, request, memberId);
  }

  @Override
  public CompletableFuture<InstallResponse> install(
      final MemberId memberId, final InstallRequest request) {
    return sendAndReceive(context.installSubject, request, memberId);
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
    return sendAndReceive(context.appendSubject, request, memberId);
  }

  @Override
  public CompletableFuture<HeartbeatResponse> heartbeat(
      final MemberId memberId, final HeartbeatRequest request) {
    return sendAndReceive(context.heartbeatSubject, request, memberId);
  }

  @Override
  public void publish(final MemberId memberId, final PublishRequest request) {
    clusterCommunicator.unicast(
        context.publishSubject(request.session()), request, serializer::encode, memberId);
  }

  @Override
  public void registerOpenSessionHandler(
      final Function<OpenSessionRequest, CompletableFuture<OpenSessionResponse>> handler) {
    clusterCommunicator.subscribe(
        context.openSessionSubject,
        serializer::decode,
        handler.<OpenSessionRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterOpenSessionHandler() {
    clusterCommunicator.unsubscribe(context.openSessionSubject);
  }

  @Override
  public void registerCloseSessionHandler(
      final Function<CloseSessionRequest, CompletableFuture<CloseSessionResponse>> handler) {
    clusterCommunicator.subscribe(
        context.closeSessionSubject,
        serializer::decode,
        handler.<CloseSessionRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterCloseSessionHandler() {
    clusterCommunicator.unsubscribe(context.closeSessionSubject);
  }

  @Override
  public void registerKeepAliveHandler(
      final Function<KeepAliveRequest, CompletableFuture<KeepAliveResponse>> handler) {
    clusterCommunicator.subscribe(
        context.keepAliveSubject,
        serializer::decode,
        handler.<KeepAliveRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterKeepAliveHandler() {
    clusterCommunicator.unsubscribe(context.keepAliveSubject);
  }

  @Override
  public void registerQueryHandler(
      final Function<QueryRequest, CompletableFuture<QueryResponse>> handler) {
    clusterCommunicator.subscribe(
        context.querySubject,
        serializer::decode,
        handler.<QueryRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterQueryHandler() {
    clusterCommunicator.unsubscribe(context.querySubject);
  }

  @Override
  public void registerCommandHandler(
      final Function<CommandRequest, CompletableFuture<CommandResponse>> handler) {
    clusterCommunicator.subscribe(
        context.commandSubject,
        serializer::decode,
        handler.<CommandRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterCommandHandler() {
    clusterCommunicator.unsubscribe(context.commandSubject);
  }

  @Override
  public void registerMetadataHandler(
      final Function<MetadataRequest, CompletableFuture<MetadataResponse>> handler) {
    clusterCommunicator.subscribe(
        context.metadataSubject,
        serializer::decode,
        handler.<MetadataRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterMetadataHandler() {
    clusterCommunicator.unsubscribe(context.metadataSubject);
  }

  @Override
  public void registerJoinHandler(
      final Function<JoinRequest, CompletableFuture<JoinResponse>> handler) {
    clusterCommunicator.subscribe(
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
    clusterCommunicator.subscribe(
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
  public void registerTransferHandler(
      final Function<TransferRequest, CompletableFuture<TransferResponse>> handler) {
    clusterCommunicator.subscribe(
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
    clusterCommunicator.subscribe(
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
    clusterCommunicator.subscribe(
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
  public void registerInstallHandler(
      final Function<InstallRequest, CompletableFuture<InstallResponse>> handler) {
    clusterCommunicator.subscribe(
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
    clusterCommunicator.subscribe(
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
    clusterCommunicator.subscribe(
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
  public void registerAppendHandler(
      final Function<AppendRequest, CompletableFuture<AppendResponse>> handler) {
    clusterCommunicator.subscribe(
        context.appendSubject,
        serializer::decode,
        handler.<AppendRequest>compose(this::recordReceivedMetrics),
        serializer::encode);
  }

  @Override
  public void unregisterAppendHandler() {
    clusterCommunicator.unsubscribe(context.appendSubject);
  }

  @Override
  public void registerResetListener(
      final SessionId sessionId, final Consumer<ResetRequest> listener, final Executor executor) {
    clusterCommunicator.subscribe(
        context.resetSubject(sessionId.id()), serializer::decode, listener, executor);
  }

  @Override
  public void unregisterResetListener(final SessionId sessionId) {
    clusterCommunicator.unsubscribe(context.resetSubject(sessionId.id()));
  }

  private <T, U> CompletableFuture<U> sendAndReceive(
      final String subject, final T request, final MemberId memberId) {
    metrics.sendMessage(memberId.id(), request.getClass().getSimpleName());
    return clusterCommunicator.send(
        subject, request, serializer::encode, serializer::decode, MemberId.from(memberId.id()));
  }

  private <T extends RaftMessage> T recordReceivedMetrics(final T m) {
    metrics.receivedMessage(m.getClass().getSimpleName());
    return m;
  }
}
