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
package io.atomix.raft.session.impl;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.event.EventType;
import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.session.SessionClient;
import io.atomix.primitive.session.SessionId;
import io.atomix.raft.ReadConsistency;
import io.atomix.raft.protocol.RaftClientProtocol;
import io.atomix.raft.session.CommunicationStrategy;
import io.atomix.raft.session.RaftSessionClient;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.logging.LoggerContext;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Handles submitting state machine {@link PrimitiveOperation operations} to the Raft cluster.
 *
 * <p>The client session is responsible for maintaining a client's connection to a Raft cluster and
 * coordinating the submission of {@link PrimitiveOperation operations} to various nodes in the
 * cluster. Client sessions are single-use objects that represent the context within which a cluster
 * can guarantee linearizable semantics for state machine operations. When a session is opened, the
 * session will register itself with the cluster by attempting to contact each of the known servers.
 * Once the session has been successfully registered, kee-alive requests will be periodically sent
 * to keep the session alive.
 *
 * <p>Sessions are responsible for sequencing concurrent operations to ensure they're applied to the
 * system state in the order in which they were submitted by the client. To do so, the session
 * coordinates with its server-side counterpart using unique per-operation sequence numbers.
 *
 * <p>In the event that the client session expires, clients are responsible for opening a new
 * session by creating and opening a new session object.
 */
public class DefaultRaftSessionClient implements RaftSessionClient {

  private final String serviceName;
  private final PrimitiveType primitiveType;
  private final ServiceConfig serviceConfig;
  private final PartitionId partitionId;
  private final Duration minTimeout;
  private final Duration maxTimeout;
  private final RaftClientProtocol protocol;
  private final MemberSelectorManager selectorManager;
  private final RaftSessionManager sessionManager;
  private final ReadConsistency readConsistency;
  private final CommunicationStrategy communicationStrategy;
  private final ThreadContext context;
  private volatile RaftSessionListener proxyListener;
  private volatile RaftSessionInvoker proxyInvoker;
  private final Consumer<MemberId> leaderChangeListener = this::onLeaderChange;
  private volatile RaftSessionState state;

  public DefaultRaftSessionClient(
      final String serviceName,
      final PrimitiveType primitiveType,
      final ServiceConfig serviceConfig,
      final PartitionId partitionId,
      final RaftClientProtocol protocol,
      final MemberSelectorManager selectorManager,
      final RaftSessionManager sessionManager,
      final ReadConsistency readConsistency,
      final CommunicationStrategy communicationStrategy,
      final ThreadContext context,
      final Duration minTimeout,
      final Duration maxTimeout) {
    this.serviceName = checkNotNull(serviceName, "serviceName cannot be null");
    this.primitiveType = checkNotNull(primitiveType, "serviceType cannot be null");
    this.serviceConfig = checkNotNull(serviceConfig, "serviceConfig cannot be null");
    this.partitionId = checkNotNull(partitionId, "partitionId cannot be null");
    this.protocol = checkNotNull(protocol, "protocol cannot be null");
    this.selectorManager = checkNotNull(selectorManager, "selectorManager cannot be null");
    this.readConsistency = checkNotNull(readConsistency, "readConsistency cannot be null");
    this.communicationStrategy =
        checkNotNull(communicationStrategy, "communicationStrategy cannot be null");
    this.context = checkNotNull(context, "context cannot be null");
    this.minTimeout = checkNotNull(minTimeout, "minTimeout cannot be null");
    this.maxTimeout = checkNotNull(maxTimeout, "maxTimeout cannot be null");
    this.sessionManager = checkNotNull(sessionManager, "sessionManager cannot be null");
  }

  @Override
  public String name() {
    return serviceName;
  }

  @Override
  public PrimitiveType type() {
    return primitiveType;
  }

  @Override
  public PrimitiveState getState() {
    return state.getState();
  }

  @Override
  public SessionId sessionId() {
    return state != null ? state.getSessionId() : null;
  }

  @Override
  public PartitionId partitionId() {
    return partitionId;
  }

  @Override
  public ThreadContext context() {
    return context;
  }

  @Override
  public CompletableFuture<byte[]> execute(final PrimitiveOperation operation) {
    final RaftSessionInvoker invoker = this.proxyInvoker;
    if (invoker == null) {
      return Futures.exceptionalFuture(new IllegalStateException("Session not open"));
    }
    return invoker.invoke(operation);
  }

  @Override
  public void addEventListener(final EventType eventType, final Consumer<PrimitiveEvent> listener) {
    if (proxyListener != null) {
      proxyListener.addEventListener(eventType, listener);
    }
  }

  @Override
  public void removeEventListener(
      final EventType eventType, final Consumer<PrimitiveEvent> listener) {
    if (proxyListener != null) {
      proxyListener.removeEventListener(eventType, listener);
    }
  }

  @Override
  public void addStateChangeListener(final Consumer<PrimitiveState> listener) {
    if (state != null) {
      state.addStateChangeListener(listener);
    }
  }

  @Override
  public void removeStateChangeListener(final Consumer<PrimitiveState> listener) {
    if (state != null) {
      state.removeStateChangeListener(listener);
    }
  }

  @Override
  public CompletableFuture<SessionClient> connect() {
    return sessionManager
        .openSession(
            serviceName,
            primitiveType,
            serviceConfig,
            readConsistency,
            communicationStrategy,
            minTimeout,
            maxTimeout)
        .thenApply(
            state -> {
              this.state = state;

              // Create command/query connections.
              final RaftSessionConnection leaderConnection =
                  new RaftSessionConnection(
                      protocol,
                      selectorManager.createSelector(CommunicationStrategy.LEADER),
                      context,
                      LoggerContext.builder(SessionClient.class)
                          .addValue(state.getSessionId())
                          .add("type", state.getPrimitiveType())
                          .add("name", state.getPrimitiveName())
                          .build());
              final RaftSessionConnection sessionConnection =
                  new RaftSessionConnection(
                      protocol,
                      selectorManager.createSelector(communicationStrategy),
                      context,
                      LoggerContext.builder(SessionClient.class)
                          .addValue(state.getSessionId())
                          .add("type", state.getPrimitiveType())
                          .add("name", state.getPrimitiveName())
                          .build());

              // Create proxy submitter/listener.
              final RaftSessionSequencer sequencer = new RaftSessionSequencer(state);
              this.proxyListener =
                  new RaftSessionListener(
                      protocol,
                      selectorManager.createSelector(CommunicationStrategy.ANY),
                      state,
                      sequencer,
                      context);
              this.proxyInvoker =
                  new RaftSessionInvoker(
                      leaderConnection,
                      sessionConnection,
                      state,
                      sequencer,
                      sessionManager,
                      context);

              selectorManager.addLeaderChangeListener(leaderChangeListener);
              state.addStateChangeListener(
                  s -> {
                    if (s == PrimitiveState.EXPIRED || s == PrimitiveState.CLOSED) {
                      selectorManager.removeLeaderChangeListener(leaderChangeListener);
                      proxyListener.close();
                      proxyInvoker.close();
                    }
                  });

              return this;
            });
  }

  @Override
  public CompletableFuture<Void> close() {
    if (state != null) {
      return sessionManager
          .closeSession(state.getSessionId(), false)
          .whenComplete((result, error) -> state.setState(PrimitiveState.CLOSED));
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> delete() {
    if (state != null) {
      return sessionManager
          .closeSession(state.getSessionId(), true)
          .whenComplete((result, error) -> state.setState(PrimitiveState.CLOSED));
    }
    return CompletableFuture.completedFuture(null);
  }

  private void onLeaderChange(final MemberId memberId) {
    if (memberId != null) {
      proxyInvoker.reset();
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(state);
  }

  @Override
  public boolean equals(final Object object) {
    return object instanceof DefaultRaftSessionClient
        && ((DefaultRaftSessionClient) object).state.getSessionId() == state.getSessionId();
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("session", state != null ? state.getSessionId() : null)
        .toString();
  }
}
