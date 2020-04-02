/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.core.test.protocol;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.event.EventType;
import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.session.SessionClient;
import io.atomix.primitive.session.SessionId;
import io.atomix.utils.concurrent.ThreadContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Test proxy session. */
public class TestSessionClient implements SessionClient {
  private final String name;
  private final PrimitiveType type;
  private final SessionId sessionId;
  private final PartitionId partitionId;
  private final ThreadContext context;
  private final TestProtocolService service;

  private final Set<Consumer<PrimitiveState>> stateChangeListeners = Sets.newConcurrentHashSet();
  private volatile PrimitiveState state = PrimitiveState.CLOSED;

  private final Map<EventType, List<Consumer<PrimitiveEvent>>> eventListeners =
      Maps.newConcurrentMap();

  TestSessionClient(
      final String name,
      final PrimitiveType type,
      final SessionId sessionId,
      final PartitionId partitionId,
      final ThreadContext context,
      final TestProtocolService service) {
    this.name = name;
    this.type = type;
    this.sessionId = sessionId;
    this.partitionId = partitionId;
    this.context = context;
    this.service = service;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public PrimitiveType type() {
    return type;
  }

  @Override
  public PrimitiveState getState() {
    return state;
  }

  @Override
  public SessionId sessionId() {
    return sessionId;
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
    final CompletableFuture<byte[]> future = new CompletableFuture<>();
    service
        .execute(sessionId, operation)
        .whenCompleteAsync(
            (result, error) -> {
              if (error == null) {
                future.complete(result);
              } else {
                future.completeExceptionally(error);
              }
            },
            context);
    return future;
  }

  @Override
  public synchronized void addEventListener(
      final EventType eventType, final Consumer<PrimitiveEvent> listener) {
    final List<Consumer<PrimitiveEvent>> listeners =
        eventListeners.computeIfAbsent(eventType, type -> Lists.newCopyOnWriteArrayList());
    if (!listeners.contains(listener)) {
      listeners.add(listener);
    }
  }

  @Override
  public synchronized void removeEventListener(
      final EventType eventType, final Consumer<PrimitiveEvent> listener) {
    final List<Consumer<PrimitiveEvent>> listeners = eventListeners.get(eventType);
    if (listeners != null && listeners.contains(listener)) {
      listeners.remove(listener);
      if (listeners.isEmpty()) {
        eventListeners.remove(eventType);
      }
    }
  }

  @Override
  public void addStateChangeListener(final Consumer<PrimitiveState> listener) {
    stateChangeListeners.add(listener);
  }

  @Override
  public void removeStateChangeListener(final Consumer<PrimitiveState> listener) {
    stateChangeListeners.remove(listener);
  }

  @Override
  public synchronized CompletableFuture<SessionClient> connect() {
    final CompletableFuture<SessionClient> future = new CompletableFuture<>();
    service
        .open(sessionId, this)
        .whenCompleteAsync(
            (result, error) -> {
              if (error == null) {
                changeState(PrimitiveState.CONNECTED);
                future.complete(this);
              } else {
                future.completeExceptionally(error);
              }
            },
            context);
    return future;
  }

  @Override
  public synchronized CompletableFuture<Void> close() {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    if (state == PrimitiveState.CLOSED) {
      future.complete(null);
    } else {
      service
          .close(sessionId)
          .whenCompleteAsync(
              (result, error) -> {
                if (error == null) {
                  changeState(PrimitiveState.CLOSED);
                  future.complete(null);
                } else {
                  future.completeExceptionally(error);
                }
              },
              context);
    }
    return future;
  }

  @Override
  public CompletableFuture<Void> delete() {
    return close().thenCompose(v -> service.delete());
  }

  /** Handles a primitive event. */
  void accept(final PrimitiveEvent event) {
    context.execute(
        () -> {
          final List<Consumer<PrimitiveEvent>> listeners = eventListeners.get(event.type());
          if (listeners != null) {
            listeners.forEach(l -> l.accept(event));
          }
        });
  }

  private synchronized void changeState(final PrimitiveState state) {
    if (this.state != state) {
      this.state = state;
      stateChangeListeners.forEach(l -> l.accept(state));
    }
  }
}
