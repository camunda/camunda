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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** Test Raft client protocol. */
public class TestRaftClientProtocol extends TestRaftProtocol implements RaftClientProtocol {

  private final Map<Long, Consumer<PublishRequest>> publishListeners = Maps.newConcurrentMap();
  private Function<HeartbeatRequest, CompletableFuture<HeartbeatResponse>> heartbeatHandler;

  public TestRaftClientProtocol(
      final MemberId memberId,
      final Map<MemberId, TestRaftServerProtocol> servers,
      final Map<MemberId, TestRaftClientProtocol> clients,
      final ThreadContext context) {
    super(servers, clients, context);
    clients.put(memberId, this);
  }

  CompletableFuture<HeartbeatResponse> heartbeat(final HeartbeatRequest request) {
    if (heartbeatHandler != null) {
      return heartbeatHandler.apply(request);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  @Override
  public CompletableFuture<OpenSessionResponse> openSession(
      final MemberId memberId, final OpenSessionRequest request) {
    return scheduleTimeout(
        getServer(memberId).thenCompose(protocol -> protocol.openSession(request)));
  }

  @Override
  public CompletableFuture<CloseSessionResponse> closeSession(
      final MemberId memberId, final CloseSessionRequest request) {
    return scheduleTimeout(
        getServer(memberId).thenCompose(protocol -> protocol.closeSession(request)));
  }

  @Override
  public CompletableFuture<KeepAliveResponse> keepAlive(
      final MemberId memberId, final KeepAliveRequest request) {
    return scheduleTimeout(
        getServer(memberId).thenCompose(protocol -> protocol.keepAlive(request)));
  }

  @Override
  public CompletableFuture<QueryResponse> query(
      final MemberId memberId, final QueryRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(protocol -> protocol.query(request)));
  }

  @Override
  public CompletableFuture<CommandResponse> command(
      final MemberId memberId, final CommandRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(protocol -> protocol.command(request)));
  }

  @Override
  public CompletableFuture<MetadataResponse> metadata(
      final MemberId memberId, final MetadataRequest request) {
    return scheduleTimeout(getServer(memberId).thenCompose(protocol -> protocol.metadata(request)));
  }

  @Override
  public void reset(final Set<MemberId> members, final ResetRequest request) {
    members.forEach(
        member -> {
          final TestRaftServerProtocol server = server(member);
          if (server != null) {
            server.reset(request);
          }
        });
  }

  @Override
  public void registerHeartbeatHandler(
      final Function<HeartbeatRequest, CompletableFuture<HeartbeatResponse>> handler) {
    this.heartbeatHandler = handler;
  }

  @Override
  public void unregisterHeartbeatHandler() {
    this.heartbeatHandler = null;
  }

  @Override
  public void registerPublishListener(
      final SessionId sessionId, final Consumer<PublishRequest> listener, final Executor executor) {
    publishListeners.put(
        sessionId.id(), request -> executor.execute(() -> listener.accept(request)));
  }

  @Override
  public void unregisterPublishListener(final SessionId sessionId) {
    publishListeners.remove(sessionId.id());
  }

  private CompletableFuture<TestRaftServerProtocol> getServer(final MemberId memberId) {
    final TestRaftServerProtocol server = server(memberId);
    if (server != null) {
      return Futures.completedFuture(server);
    } else {
      return Futures.exceptionalFuture(new ConnectException());
    }
  }

  void publish(final PublishRequest request) {
    final Consumer<PublishRequest> listener = publishListeners.get(request.session());
    if (listener != null) {
      listener.accept(request);
    }
  }
}
