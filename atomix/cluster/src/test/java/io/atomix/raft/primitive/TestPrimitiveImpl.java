/*
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
package io.atomix.raft.primitive;

import com.google.common.collect.Sets;
import io.atomix.primitive.AbstractAsyncPrimitive;
import io.atomix.primitive.PrimitiveRegistry;
import io.atomix.primitive.SyncPrimitive;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.proxy.ProxyClient;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class TestPrimitiveImpl extends AbstractAsyncPrimitive<TestPrimitive, TestPrimitiveService>
    implements TestPrimitive, TestPrimitiveClient {

  private final Set<Consumer<Long>> eventListeners = Sets.newCopyOnWriteArraySet();
  private final Set<Consumer<String>> expireListeners = Sets.newCopyOnWriteArraySet();
  private final Set<Consumer<String>> closeListeners = Sets.newCopyOnWriteArraySet();

  public TestPrimitiveImpl(
      final ProxyClient<TestPrimitiveService> proxy, final PrimitiveRegistry registry) {
    super(proxy, registry);
  }

  @Override
  public PrimitiveProtocol protocol() {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Long> write(final String value) {
    return getProxyClient().applyBy(name(), service -> service.write(value));
  }

  @Override
  public CompletableFuture<Long> read() {
    return getProxyClient().applyBy(name(), service -> service.read());
  }

  @Override
  public CompletableFuture<Long> sendEvent(final boolean sender) {
    return getProxyClient().applyBy(name(), service -> service.sendEvent(sender));
  }

  @Override
  public CompletableFuture<Void> onEvent(final Consumer<Long> callback) {
    eventListeners.add(callback);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> onExpire(final Consumer<String> callback) {
    expireListeners.add(callback);
    return getProxyClient().acceptBy(name(), service -> service.onExpire());
  }

  @Override
  public CompletableFuture<Void> onClose(final Consumer<String> callback) {
    closeListeners.add(callback);
    return getProxyClient().acceptBy(name(), service -> service.onClose());
  }

  @Override
  public void event(final long index) {
    eventListeners.forEach(l -> l.accept(index));
  }

  @Override
  public void expire(final String value) {
    expireListeners.forEach(l -> l.accept(value));
  }

  @Override
  public void close(final String value) {
    closeListeners.forEach(l -> l.accept(value));
  }

  @Override
  public SyncPrimitive sync() {
    return null;
  }

  @Override
  public SyncPrimitive sync(final Duration operationTimeout) {
    return null;
  }
}
