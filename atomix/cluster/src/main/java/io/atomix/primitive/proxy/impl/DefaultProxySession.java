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
package io.atomix.primitive.proxy.impl;

import com.google.common.base.Defaults;
import io.atomix.primitive.PrimitiveException;
import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.event.Events;
import io.atomix.primitive.operation.OperationId;
import io.atomix.primitive.operation.Operations;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.proxy.ProxySession;
import io.atomix.primitive.session.SessionClient;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.serializer.Serializer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lazy partition proxy. */
public class DefaultProxySession<S> implements ProxySession<S> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final SessionClient session;
  private final Serializer serializer;
  private final ServiceProxy<S> proxy;
  private volatile CompletableFuture<ProxySession<S>> connectFuture;
  private volatile boolean closed;

  @SuppressWarnings("unchecked")
  public DefaultProxySession(
      final SessionClient session, final Class<S> serviceType, final Serializer serializer) {
    this.session = session;
    this.serializer = serializer;
    final ServiceProxyHandler serviceProxyHandler = new ServiceProxyHandler(serviceType);
    final S serviceProxy =
        (S)
            java.lang.reflect.Proxy.newProxyInstance(
                serviceType.getClassLoader(), new Class[] {serviceType}, serviceProxyHandler);
    proxy = new ServiceProxy<>(serviceProxy, serviceProxyHandler);
  }

  @Override
  public String name() {
    return session.name();
  }

  @Override
  public PrimitiveType type() {
    return session.type();
  }

  @Override
  public PartitionId partitionId() {
    return session.partitionId();
  }

  @Override
  public ThreadContext context() {
    return session.context();
  }

  @Override
  public PrimitiveState getState() {
    return session.getState();
  }

  @Override
  public void register(final Object client) {
    Events.getEventMap(client.getClass())
        .forEach(
            (eventType, method) -> {
              session.addEventListener(
                  eventType,
                  event -> {
                    try {
                      method.invoke(client, (Object[]) decode(event.value()));
                    } catch (final IllegalAccessException | InvocationTargetException e) {
                      log.warn("Failed to handle event", e);
                    }
                  });
            });
  }

  @Override
  public CompletableFuture<Void> accept(final Consumer<S> operation) {
    if (closed) {
      return Futures.exceptionalFuture(new PrimitiveException.ClosedSession());
    }
    return proxy.accept(operation);
  }

  @Override
  public <R> CompletableFuture<R> apply(final Function<S, R> operation) {
    if (closed) {
      return Futures.exceptionalFuture(new PrimitiveException.ClosedSession());
    }
    return proxy.apply(operation);
  }

  @Override
  public void addStateChangeListener(final Consumer<PrimitiveState> listener) {
    session.addStateChangeListener(listener);
  }

  @Override
  public void removeStateChangeListener(final Consumer<PrimitiveState> listener) {
    session.removeStateChangeListener(listener);
  }

  @Override
  public CompletableFuture<ProxySession<S>> connect() {
    if (connectFuture == null) {
      synchronized (this) {
        if (connectFuture == null) {
          connectFuture = session.connect().thenApply(v -> this);
        }
      }
    }
    return connectFuture;
  }

  @Override
  public CompletableFuture<Void> close() {
    return session.close().thenRun(() -> closed = true);
  }

  @Override
  public CompletableFuture<Void> delete() {
    return session.delete().thenRun(() -> closed = true);
  }

  /**
   * Returns the serializer for the primitive operations.
   *
   * @return the serializer for the primitive operations
   */
  protected Serializer serializer() {
    return serializer;
  }

  /**
   * Encodes the given object using the configured {@link #serializer()}.
   *
   * @param object the object to encode
   * @param <T> the object type
   * @return the encoded bytes
   */
  protected <T> byte[] encode(final T object) {
    return object != null ? serializer().encode(object) : null;
  }

  /**
   * Decodes the given object using the configured {@link #serializer()}.
   *
   * @param bytes the bytes to decode
   * @param <T> the object type
   * @return the decoded object
   */
  protected <T> T decode(final byte[] bytes) {
    return bytes != null ? serializer().decode(bytes) : null;
  }

  /** Service proxy container. */
  private class ServiceProxy<S> {
    private final S proxy;
    private final ServiceProxyHandler handler;

    ServiceProxy(final S proxy, final ServiceProxyHandler handler) {
      this.proxy = proxy;
      this.handler = handler;
    }

    /**
     * Invokes a void method on the underlying proxy.
     *
     * @param operation the operation to perform on the proxy
     * @return the resulting void future
     */
    CompletableFuture<Void> accept(final Consumer<S> operation) {
      operation.accept(proxy);
      return handler.getResultFuture();
    }

    /**
     * Invokes a function on the underlying proxy.
     *
     * @param operation the operation to perform on the proxy
     * @param <T> the operation return type
     * @return the future result
     */
    <T> CompletableFuture<T> apply(final Function<S, T> operation) {
      operation.apply(proxy);
      return handler.getResultFuture();
    }
  }

  /**
   * Service proxy invocation handler.
   *
   * <p>The invocation handler
   */
  private final class ServiceProxyHandler implements InvocationHandler {
    private final ThreadLocal<CompletableFuture> future = new ThreadLocal<>();
    private final Map<Method, OperationId> operations = new ConcurrentHashMap<>();

    private ServiceProxyHandler(final Class<?> type) {
      this.operations.putAll(Operations.getMethodMap(type));
    }

    @Override
    public Object invoke(final Object object, final Method method, final Object[] args)
        throws Throwable {
      final OperationId operationId = operations.get(method);
      if (operationId != null) {
        future.set(
            session
                .execute(PrimitiveOperation.operation(operationId, encode(args)))
                .thenApply(DefaultProxySession.this::decode));
      } else {
        throw new PrimitiveException("Unknown primitive operation: " + method.getName());
      }
      return Defaults.defaultValue(method.getReturnType());
    }

    /**
     * Returns the result future for the operation.
     *
     * @param <T> the future result type
     * @return the result future
     */
    @SuppressWarnings("unchecked")
    <T> CompletableFuture<T> getResultFuture() {
      return future.get();
    }
  }
}
