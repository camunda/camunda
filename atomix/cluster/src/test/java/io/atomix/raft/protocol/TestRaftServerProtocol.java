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
package io.atomix.raft.protocol;

import com.google.common.collect.Maps;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.session.SessionId;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.ThreadContext;
import java.net.ConnectException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** Test server protocol. */
public class TestRaftServerProtocol extends TestRaftProtocol implements RaftServerProtocol {

  private final Map<Long, Consumer<ResetRequest>> resetListeners = Maps.newConcurrentMap();
  private Function<OpenSessionRequest, CompletableFuture<OpenSessionResponse>> openSessionHandler;
  private Function<CloseSessionRequest, CompletableFuture<CloseSessionResponse>>
      closeSessionHandler;
  private Function<KeepAliveRequest, CompletableFuture<KeepAliveResponse>> keepAliveHandler;
  private Function<QueryRequest, CompletableFuture<QueryResponse>> queryHandler;
  private Function<CommandRequest, CompletableFuture<CommandResponse>> commandHandler;
  private Function<MetadataRequest, CompletableFuture<MetadataResponse>> metadataHandler;
  private Function<JoinRequest, CompletableFuture<JoinResponse>> joinHandler;
  private Function<LeaveRequest, CompletableFuture<LeaveResponse>> leaveHandler;
  private Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> configureHandler;
  private Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> reconfigureHandler;
  private Function<InstallRequest, CompletableFuture<InstallResponse>> installHandler;
  private Function<TransferRequest, CompletableFuture<TransferResponse>> transferHandler;
  private Function<PollRequest, CompletableFuture<PollResponse>> pollHandler;
  private Function<VoteRequest, CompletableFuture<VoteResponse>> voteHandler;
  private Function<AppendRequest, CompletableFuture<AppendResponse>> appendHandler;

  public TestRaftServerProtocol(
      final MemberId memberId,
      final Map<MemberId, TestRaftServerProtocol> servers,
      final Map<MemberId, TestRaftClientProtocol> clients,
      final ThreadContext context) {
    super(servers, clients, context);
    servers.put(memberId, this);
  }

  @Override
  public CompletableFuture<OpenSessionResponse> openSession(
      final MemberId memberId, final OpenSessionRequest request) {
    return scheduleTimeout(
        getServer(memberId).thenCompose(listener -> listener.openSession(request)));
  }

  @Override
  public CompletableFuture<CloseSessionResponse> closeSession(
      final MemberId memberId, final CloseSessionRequest request) {
    return scheduleTimeout(
        getServer(memberId).thenCompose(listener -> listener.closeSession(request)));
  }

  @Override
  public CompletableFuture<KeepAliveResponse> keepAlive(
      final MemberId memberId, final KeepAliveRequest request) {
    return scheduleTimeout(
        getServer(memberId).thenCompose(listener -> listener.keepAlive(request)));
  }

  @Override
  public CompletableFuture<QueryResponse> query(
      final MemberId memberId, final QueryRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.query(request)));
  }

  @Override
  public CompletableFuture<CommandResponse> command(
      final MemberId memberId, final CommandRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.command(request)));
  }

  @Override
  public CompletableFuture<MetadataResponse> metadata(
      final MemberId memberId, final MetadataRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.metadata(request)));
  }

  @Override
  public CompletableFuture<JoinResponse> join(final MemberId memberId, final JoinRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.join(request)));
  }

  @Override
  public CompletableFuture<LeaveResponse> leave(
      final MemberId memberId, final LeaveRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.leave(request)));
  }

  @Override
  public CompletableFuture<ConfigureResponse> configure(
      final MemberId memberId, final ConfigureRequest request) {
    return scheduleTimeout(
        getServer(memberId).thenCompose(listener -> listener.configure(request)));
  }

  @Override
  public CompletableFuture<ReconfigureResponse> reconfigure(
      final MemberId memberId, final ReconfigureRequest request) {
    return scheduleTimeout(
        getServer(memberId).thenCompose(listener -> listener.reconfigure(request)));
  }

  @Override
  public CompletableFuture<InstallResponse> install(
      final MemberId memberId, final InstallRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.install(request)));
  }

  @Override
  public CompletableFuture<TransferResponse> transfer(
      final MemberId memberId, final TransferRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.transfer(request)));
  }

  @Override
  public CompletableFuture<PollResponse> poll(final MemberId memberId, final PollRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.poll(request)));
  }

  @Override
  public CompletableFuture<VoteResponse> vote(final MemberId memberId, final VoteRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.vote(request)));
  }

  @Override
  public CompletableFuture<AppendResponse> append(
      final MemberId memberId, final AppendRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(listener -> listener.append(request)));
  }

  @Override
  public CompletableFuture<HeartbeatResponse> heartbeat(
      final MemberId memberId, final HeartbeatRequest request) {
    return scheduleTimeout(
        getClient(memberId).thenCompose(protocol -> protocol.heartbeat(request)));
  }

  @Override
  public void publish(final MemberId memberId, final PublishRequest request) {
    getClient(memberId).thenAccept(protocol -> protocol.publish(request));
  }

  @Override
  public void registerOpenSessionHandler(
      final Function<OpenSessionRequest, CompletableFuture<OpenSessionResponse>> handler) {
    this.openSessionHandler = handler;
  }

  @Override
  public void unregisterOpenSessionHandler() {
    this.openSessionHandler = null;
  }

  @Override
  public void registerCloseSessionHandler(
      final Function<CloseSessionRequest, CompletableFuture<CloseSessionResponse>> handler) {
    this.closeSessionHandler = handler;
  }

  @Override
  public void unregisterCloseSessionHandler() {
    this.closeSessionHandler = null;
  }

  @Override
  public void registerKeepAliveHandler(
      final Function<KeepAliveRequest, CompletableFuture<KeepAliveResponse>> handler) {
    this.keepAliveHandler = handler;
  }

  @Override
  public void unregisterKeepAliveHandler() {
    this.keepAliveHandler = null;
  }

  @Override
  public void registerQueryHandler(
      final Function<QueryRequest, CompletableFuture<QueryResponse>> handler) {
    this.queryHandler = handler;
  }

  @Override
  public void unregisterQueryHandler() {
    this.queryHandler = null;
  }

  @Override
  public void registerCommandHandler(
      final Function<CommandRequest, CompletableFuture<CommandResponse>> handler) {
    this.commandHandler = handler;
  }

  @Override
  public void unregisterCommandHandler() {
    this.commandHandler = null;
  }

  @Override
  public void registerMetadataHandler(
      final Function<MetadataRequest, CompletableFuture<MetadataResponse>> handler) {
    this.metadataHandler = handler;
  }

  @Override
  public void unregisterMetadataHandler() {
    this.metadataHandler = null;
  }

  @Override
  public void registerJoinHandler(
      final Function<JoinRequest, CompletableFuture<JoinResponse>> handler) {
    this.joinHandler = handler;
  }

  @Override
  public void unregisterJoinHandler() {
    this.joinHandler = null;
  }

  @Override
  public void registerLeaveHandler(
      final Function<LeaveRequest, CompletableFuture<LeaveResponse>> handler) {
    this.leaveHandler = handler;
  }

  @Override
  public void unregisterLeaveHandler() {
    this.leaveHandler = null;
  }

  @Override
  public void registerTransferHandler(
      final Function<TransferRequest, CompletableFuture<TransferResponse>> handler) {
    this.transferHandler = handler;
  }

  @Override
  public void unregisterTransferHandler() {
    this.transferHandler = null;
  }

  @Override
  public void registerConfigureHandler(
      final Function<ConfigureRequest, CompletableFuture<ConfigureResponse>> handler) {
    this.configureHandler = handler;
  }

  @Override
  public void unregisterConfigureHandler() {
    this.configureHandler = null;
  }

  @Override
  public void registerReconfigureHandler(
      final Function<ReconfigureRequest, CompletableFuture<ReconfigureResponse>> handler) {
    this.reconfigureHandler = handler;
  }

  @Override
  public void unregisterReconfigureHandler() {
    this.reconfigureHandler = null;
  }

  @Override
  public void registerInstallHandler(
      final Function<InstallRequest, CompletableFuture<InstallResponse>> handler) {
    this.installHandler = handler;
  }

  @Override
  public void unregisterInstallHandler() {
    this.installHandler = null;
  }

  @Override
  public void registerPollHandler(
      final Function<PollRequest, CompletableFuture<PollResponse>> handler) {
    this.pollHandler = handler;
  }

  @Override
  public void unregisterPollHandler() {
    this.pollHandler = null;
  }

  @Override
  public void registerVoteHandler(
      final Function<VoteRequest, CompletableFuture<VoteResponse>> handler) {
    this.voteHandler = handler;
  }

  @Override
  public void unregisterVoteHandler() {
    this.voteHandler = null;
  }

  @Override
  public void registerAppendHandler(
      final Function<AppendRequest, CompletableFuture<AppendResponse>> handler) {
    this.appendHandler = handler;
  }

  @Override
  public void unregisterAppendHandler() {
    this.appendHandler = null;
  }

  @Override
  public void registerResetListener(
      final SessionId sessionId, final Consumer<ResetRequest> listener, final Executor executor) {
    resetListeners.put(sessionId.id(), request -> executor.execute(() -> listener.accept(request)));
  }

  @Override
  public void unregisterResetListener(final SessionId sessionId) {
    resetListeners.remove(sessionId.id());
  }

  private CompletableFuture<TestRaftServerProtocol> getServer(final MemberId memberId) {
    final TestRaftServerProtocol server = server(memberId);
    if (server != null) {
      return Futures.completedFuture(server);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  private CompletableFuture<TestRaftClientProtocol> getClient(final MemberId memberId) {
    final TestRaftClientProtocol client = client(memberId);
    if (client != null) {
      return Futures.completedFuture(client);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<AppendResponse> append(final AppendRequest request) {
    if (appendHandler != null) {
      return appendHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<VoteResponse> vote(final VoteRequest request) {
    if (voteHandler != null) {
      return voteHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<PollResponse> poll(final PollRequest request) {
    if (pollHandler != null) {
      return pollHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<TransferResponse> transfer(final TransferRequest request) {
    if (transferHandler != null) {
      return transferHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<InstallResponse> install(final InstallRequest request) {
    if (installHandler != null) {
      return installHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<ReconfigureResponse> reconfigure(final ReconfigureRequest request) {
    if (reconfigureHandler != null) {
      return reconfigureHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<ConfigureResponse> configure(final ConfigureRequest request) {
    if (configureHandler != null) {
      return configureHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<LeaveResponse> leave(final LeaveRequest request) {
    if (leaveHandler != null) {
      return leaveHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<JoinResponse> join(final JoinRequest request) {
    if (joinHandler != null) {
      return joinHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<MetadataResponse> metadata(final MetadataRequest request) {
    if (metadataHandler != null) {
      return metadataHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<CommandResponse> command(final CommandRequest request) {
    if (commandHandler != null) {
      return commandHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<QueryResponse> query(final QueryRequest request) {
    if (queryHandler != null) {
      return queryHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<KeepAliveResponse> keepAlive(final KeepAliveRequest request) {
    if (keepAliveHandler != null) {
      return keepAliveHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<CloseSessionResponse> closeSession(final CloseSessionRequest request) {
    if (closeSessionHandler != null) {
      return closeSessionHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  CompletableFuture<OpenSessionResponse> openSession(final OpenSessionRequest request) {
    if (openSessionHandler != null) {
      return openSessionHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  void reset(final ResetRequest request) {
    final Consumer<ResetRequest> listener = resetListeners.get(request.session());
    if (listener != null) {
      listener.accept(request);
    }
  }
}
