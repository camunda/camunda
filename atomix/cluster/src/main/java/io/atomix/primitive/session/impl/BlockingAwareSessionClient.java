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
package io.atomix.primitive.session.impl;

import static io.atomix.utils.concurrent.Futures.asyncFuture;
import static io.atomix.utils.concurrent.Futures.orderedFuture;

import com.google.common.collect.Maps;
import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.event.EventType;
import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.primitive.session.SessionClient;
import io.atomix.utils.concurrent.ThreadContext;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Session client delegate that completes futures on a thread pool. */
public class BlockingAwareSessionClient extends DelegatingSessionClient {
  private final ThreadContext context;
  private final Map<Consumer<PrimitiveState>, Consumer<PrimitiveState>> stateChangeListeners =
      Maps.newConcurrentMap();
  private final Map<Consumer<PrimitiveEvent>, Consumer<PrimitiveEvent>> eventListeners =
      Maps.newConcurrentMap();
  private volatile CompletableFuture<SessionClient> connectFuture;
  private volatile CompletableFuture<Void> closeFuture;

  public BlockingAwareSessionClient(final SessionClient session, final ThreadContext context) {
    super(session);
    this.context = context;
  }

  @Override
  public CompletableFuture<byte[]> execute(final PrimitiveOperation operation) {
    return asyncFuture(super.execute(operation), context);
  }

  @Override
  public void addEventListener(final EventType eventType, final Consumer<PrimitiveEvent> listener) {
    final Consumer<PrimitiveEvent> wrappedListener = e -> context.execute(() -> listener.accept(e));
    eventListeners.put(listener, wrappedListener);
    super.addEventListener(eventType, wrappedListener);
  }

  @Override
  public void removeEventListener(
      final EventType eventType, final Consumer<PrimitiveEvent> listener) {
    final Consumer<PrimitiveEvent> wrappedListener = eventListeners.remove(listener);
    if (wrappedListener != null) {
      super.removeEventListener(eventType, wrappedListener);
    }
  }

  @Override
  public void addStateChangeListener(final Consumer<PrimitiveState> listener) {
    final Consumer<PrimitiveState> wrappedListener =
        state -> context.execute(() -> listener.accept(state));
    stateChangeListeners.put(listener, wrappedListener);
    super.addStateChangeListener(wrappedListener);
  }

  @Override
  public void removeStateChangeListener(final Consumer<PrimitiveState> listener) {
    final Consumer<PrimitiveState> wrappedListener = stateChangeListeners.remove(listener);
    if (wrappedListener != null) {
      super.removeStateChangeListener(wrappedListener);
    }
  }

  @Override
  public CompletableFuture<SessionClient> connect() {
    if (connectFuture == null) {
      synchronized (this) {
        if (connectFuture == null) {
          connectFuture = orderedFuture(asyncFuture(super.connect(), context));
        }
      }
    }
    return connectFuture;
  }

  @Override
  public CompletableFuture<Void> close() {
    if (closeFuture == null) {
      synchronized (this) {
        if (closeFuture == null) {
          closeFuture = orderedFuture(asyncFuture(super.close(), context));
        }
      }
    }
    return closeFuture;
  }

  @Override
  public CompletableFuture<Void> delete() {
    if (closeFuture == null) {
      synchronized (this) {
        if (closeFuture == null) {
          closeFuture = orderedFuture(asyncFuture(super.delete(), context));
        }
      }
    }
    return closeFuture;
  }
}
