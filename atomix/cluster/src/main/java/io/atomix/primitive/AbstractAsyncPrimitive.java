/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.proxy.ProxyClient;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Abstract base class for primitives that interact with Raft replicated state machines via proxy.
 */
public abstract class AbstractAsyncPrimitive<A extends AsyncPrimitive, S>
    implements AsyncPrimitive {
  private final ProxyClient<S> client;
  private final PrimitiveRegistry registry;

  protected AbstractAsyncPrimitive(final ProxyClient<S> client, final PrimitiveRegistry registry) {
    this.client = checkNotNull(client, "proxy cannot be null");
    this.registry = checkNotNull(registry, "registry cannot be null");
    client.register(this);
  }

  @Override
  public String name() {
    return client.name();
  }

  @Override
  public PrimitiveType type() {
    return client.type();
  }

  @Override
  public PrimitiveProtocol protocol() {
    return client.protocol();
  }

  @Override
  public void addStateChangeListener(final Consumer<PrimitiveState> listener) {
    client.addStateChangeListener(listener);
  }

  @Override
  public void removeStateChangeListener(final Consumer<PrimitiveState> listener) {
    client.removeStateChangeListener(listener);
  }

  /**
   * Returns the primitive proxy client.
   *
   * @return the primitive proxy client
   */
  protected ProxyClient<S> getProxyClient() {
    return client;
  }

  /**
   * Connects the primitive.
   *
   * @return a future to be completed once the primitive has been connected
   */
  @SuppressWarnings("unchecked")
  public CompletableFuture<A> connect() {
    return registry.createPrimitive(name(), type()).thenApply(v -> (A) this);
  }

  @Override
  public CompletableFuture<Void> close() {
    return client.close();
  }

  @Override
  public CompletableFuture<Void> delete() {
    return client.delete().thenCompose(v -> registry.deletePrimitive(name()));
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("proxy", client).toString();
  }
}
