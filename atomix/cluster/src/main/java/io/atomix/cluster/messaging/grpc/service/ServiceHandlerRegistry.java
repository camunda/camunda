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
package io.atomix.cluster.messaging.grpc.service;

import com.google.common.util.concurrent.MoreExecutors;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.EmptyResponse;
import io.camunda.zeebe.messaging.protocol.MessagingOuterClass.Response;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public final class ServiceHandlerRegistry {
  private final ConcurrentMap<
          String, ConcurrentMap<BiConsumer<Address, byte[]>, RequestHandler<EmptyResponse>>>
      unicastHandlers = new ConcurrentHashMap<>();

  private final ConcurrentMap<String, RequestHandler<Response>> messagingHandlers =
      new ConcurrentHashMap<>();

  public Collection<RequestHandler<EmptyResponse>> getUnicastHandlers(final String type) {
    return unicastHandlers.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>()).values();
  }

  public RequestHandler<Response> getMessagingHandler(final String type) {
    return messagingHandlers.get(type);
  }

  public void addMessagingHandler(
      final String type, final BiConsumer<Address, byte[]> handler, final Executor executor) {
    addMessagingHandler(type, new EmptyResponseMessagingAdapter(handler), executor);
  }

  public void addMessagingHandler(
      final String type,
      final BiFunction<Address, byte[], byte[]> handler,
      final Executor executor) {
    messagingHandlers.put(type, new SyncMessagingHandler(type, executor, handler));
  }

  public void addMessagingHandler(
      final String type, final BiFunction<Address, byte[], CompletableFuture<byte[]>> handler) {
    messagingHandlers.put(
        type, new AsyncMessagingHandler(type, MoreExecutors.directExecutor(), handler));
  }

  public void removeMessagingHandler(final String type) {
    messagingHandlers.remove(type);
  }

  public void addUnicastHandler(
      final String type, final BiConsumer<Address, byte[]> handler, final Executor executor) {
    unicastHandlers
        .computeIfAbsent(type, ignored -> new ConcurrentHashMap<>())
        .put(handler, new UnicastHandler(type, executor, handler));
  }

  public void removeUnicastHandler(final String type) {
    unicastHandlers.remove(type);
  }

  public void clear() {
    unicastHandlers.clear();
  }

  private static final class EmptyResponseMessagingAdapter
      implements BiFunction<Address, byte[], byte[]> {
    private static final byte[] EMPTY = new byte[0];
    private final BiConsumer<Address, byte[]> delegate;

    public EmptyResponseMessagingAdapter(final BiConsumer<Address, byte[]> delegate) {
      this.delegate = delegate;
    }

    @Override
    public byte[] apply(final Address address, final byte[] bytes) {
      delegate.accept(address, bytes);
      return EMPTY;
    }
  }
}
