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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Defaults;
import com.google.common.collect.Maps;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveException;
import io.atomix.primitive.PrimitiveId;
import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.event.EventType;
import io.atomix.primitive.event.Events;
import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.log.LogRecord;
import io.atomix.primitive.log.LogSession;
import io.atomix.primitive.operation.OperationId;
import io.atomix.primitive.operation.OperationType;
import io.atomix.primitive.operation.Operations;
import io.atomix.primitive.operation.impl.DefaultOperationId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.proxy.ProxySession;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.service.ServiceContext;
import io.atomix.primitive.service.impl.DefaultCommit;
import io.atomix.primitive.session.Session;
import io.atomix.primitive.session.SessionId;
import io.atomix.primitive.session.impl.AbstractSession;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.atomix.utils.serializer.Serializer;
import io.atomix.utils.time.LogicalClock;
import io.atomix.utils.time.LogicalTimestamp;
import io.atomix.utils.time.WallClock;
import io.atomix.utils.time.WallClockTimestamp;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Log proxy session. */
public class LogProxySession<S> implements ProxySession<S> {
  private static final Serializer INTERNAL_SERIALIZER =
      Serializer.using(
          Namespace.builder()
              .register(Namespaces.BASIC)
              .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
              .register(LogOperation.class)
              .register(DefaultOperationId.class)
              .register(OperationType.class)
              .register(SessionId.class)
              .build());

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String name;
  private final PrimitiveType type;
  private final PrimitiveService service;
  private final ServiceConfig serviceConfig;
  private final ServiceContext context = new LogServiceContext();
  private final Serializer userSerializer;
  private final LogSession session;
  private final ServiceProxy<S> proxy;
  private volatile Object client;
  private volatile CompletableFuture<ProxySession<S>> connectFuture;
  private final AtomicLong operationIndex = new AtomicLong();
  private final Map<EventType, Method> eventMethods = Maps.newConcurrentMap();
  private final Map<Long, CompletableFuture> writeFutures = Maps.newConcurrentMap();
  private final Queue<PendingRead> pendingReads = new LinkedList<>();
  private final Map<SessionId, Session> sessions = Maps.newConcurrentMap();
  private long lastIndex;
  private long currentIndex;
  private Session currentSession;
  private OperationType currentOperation;
  private long currentTimestamp;

  @SuppressWarnings("unchecked")
  public LogProxySession(
      final String name,
      final PrimitiveType type,
      final Class<S> serviceType,
      final ServiceConfig serviceConfig,
      final Serializer serializer,
      final LogSession session) {
    this.name = checkNotNull(name, "name cannot be null");
    this.type = checkNotNull(type, "type cannot be null");
    this.service = type.newService(serviceConfig);
    this.serviceConfig = serviceConfig;
    this.userSerializer = checkNotNull(serializer, "serializer cannot be null");
    this.session = checkNotNull(session, "session cannot be null");
    final ServiceProxyHandler serviceProxyHandler = new ServiceProxyHandler(serviceType);
    final S serviceProxy =
        (S)
            java.lang.reflect.Proxy.newProxyInstance(
                serviceType.getClassLoader(), new Class[] {serviceType}, serviceProxyHandler);
    proxy = new ServiceProxy<>(serviceProxy, serviceProxyHandler);
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
    this.client = client;
    Events.getEventMap(client.getClass())
        .forEach((eventType, method) -> eventMethods.put(eventType, method));
  }

  @Override
  public CompletableFuture<Void> accept(final Consumer<S> operation) {
    if (session.getState() == PrimitiveState.CLOSED) {
      return Futures.exceptionalFuture(new PrimitiveException.ClosedSession());
    }
    return proxy.accept(operation);
  }

  @Override
  public <R> CompletableFuture<R> apply(final Function<S, R> operation) {
    if (session.getState() == PrimitiveState.CLOSED) {
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
          session.consumer().consume(1, this::consume);
          service.init(context);
          connectFuture = session.connect().thenApply(v -> this);
        }
      }
    }
    return connectFuture;
  }

  @Override
  public CompletableFuture<Void> close() {
    return session.close();
  }

  @Override
  public CompletableFuture<Void> delete() {
    return close();
  }

  /**
   * Gets or creates a session.
   *
   * @param sessionId the session identifier
   * @return the session
   */
  private Session getOrCreateSession(final SessionId sessionId) {
    Session session = sessions.get(sessionId);
    if (session == null) {
      session = new LocalSession(sessionId, name(), type(), null, service.serializer());
      sessions.put(session.sessionId(), session);
      service.register(session);
    }
    return session;
  }

  /**
   * Consumes a record from the log.
   *
   * @param record the record to consume
   */
  @SuppressWarnings("unchecked")
  private void consume(final LogRecord record) {
    // Decode the raw log operation from the record.
    final LogOperation operation = decodeInternal(record.value());

    // If this operation is not destined for this primitive, ignore it.
    // TODO: If multiple primitives of different types are created and destroyed on the same
    // distributed log,
    // we need to be able to differentiate between different instances of a service by the service
    // ID.
    if (!operation.primitive().equals(name())) {
      return;
    }

    // Create a session from the log record.
    Session session = getOrCreateSession(operation.sessionId());

    // Update the local context for the service.
    currentIndex = record.index();
    currentSession = session;
    currentOperation = operation.operationId().type();
    currentTimestamp = record.timestamp();

    // Apply the operation to the service.
    byte[] output =
        service.apply(
            new DefaultCommit<>(
                currentIndex,
                operation.operationId(),
                operation.operation(),
                currentSession,
                currentTimestamp));

    // If the operation session matches the local session, complete the write future.
    if (operation.sessionId().equals(this.session.sessionId())) {
      final CompletableFuture future = writeFutures.remove(operation.operationIndex());
      if (future != null) {
        future.complete(decode(output));
      }
    }

    // Iterate through pending reads and complete any reads at indexes less than or equal to the
    // applied index.
    PendingRead pendingRead = pendingReads.peek();
    while (pendingRead != null && pendingRead.index <= record.index()) {
      session = getOrCreateSession(this.session.sessionId());
      currentSession = session;
      currentOperation = OperationType.QUERY;
      try {
        output =
            service.apply(
                new DefaultCommit<>(
                    currentIndex,
                    pendingRead.operationId,
                    pendingRead.bytes,
                    session,
                    currentTimestamp));
        pendingRead.future.complete(output);
      } catch (final Exception e) {
        pendingRead.future.completeExceptionally(new PrimitiveException.ServiceException());
      }
      pendingReads.remove();
      pendingRead = pendingReads.peek();
    }
  }

  /**
   * Encodes the given object using the configured {@link #userSerializer}.
   *
   * @param object the object to encode
   * @param <T> the object type
   * @return the encoded bytes
   */
  protected <T> byte[] encode(final T object) {
    return object != null ? userSerializer.encode(object) : null;
  }

  /**
   * Decodes the given object using the configured {@link #userSerializer}.
   *
   * @param bytes the bytes to decode
   * @param <T> the object type
   * @return the decoded object
   */
  protected <T> T decode(final byte[] bytes) {
    return bytes != null ? userSerializer.decode(bytes) : null;
  }

  /**
   * Encodes an internal object.
   *
   * @param object the object to encode
   * @param <T> the object type
   * @return the encoded bytes
   */
  private <T> byte[] encodeInternal(final T object) {
    return INTERNAL_SERIALIZER.encode(object);
  }

  /**
   * Decodes an internal object.
   *
   * @param bytes the bytes to decode
   * @param <T> the object type
   * @return the internal object
   */
  private <T> T decodeInternal(final byte[] bytes) {
    return INTERNAL_SERIALIZER.decode(bytes);
  }

  /** Pending read operation. */
  private static class PendingRead {
    private final long index;
    private final OperationId operationId;
    private final byte[] bytes;
    private final CompletableFuture future;

    PendingRead(
        final long index,
        final OperationId operationId,
        final byte[] bytes,
        final CompletableFuture future) {
      this.index = index;
      this.operationId = operationId;
      this.bytes = bytes;
      this.future = future;
    }
  }

  /** Log service context. */
  private class LogServiceContext implements ServiceContext {
    @Override
    public PrimitiveId serviceId() {
      return PrimitiveId.from(session.sessionId().id());
    }

    @Override
    public String serviceName() {
      return name;
    }

    @Override
    public PrimitiveType serviceType() {
      return type;
    }

    @Override
    public MemberId localMemberId() {
      return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends ServiceConfig> C serviceConfig() {
      return (C) serviceConfig;
    }

    @Override
    public long currentIndex() {
      return currentIndex;
    }

    @Override
    public Session currentSession() {
      return currentSession;
    }

    @Override
    public OperationType currentOperation() {
      return currentOperation;
    }

    @Override
    public LogicalClock logicalClock() {
      return new LogicalClock() {
        @Override
        public LogicalTimestamp getTime() {
          return LogicalTimestamp.of(currentIndex);
        }
      };
    }

    @Override
    public WallClock wallClock() {
      return new WallClock() {
        @Override
        public WallClockTimestamp getTime() {
          return WallClockTimestamp.from(currentTimestamp);
        }
      };
    }
  }

  /** Local session. */
  private class LocalSession extends AbstractSession {
    LocalSession(
        final SessionId sessionId,
        final String primitiveName,
        final PrimitiveType primitiveType,
        final MemberId memberId,
        final Serializer serializer) {
      super(sessionId, primitiveName, primitiveType, memberId, serializer);
    }

    @Override
    public State getState() {
      return State.OPEN;
    }

    @Override
    public void publish(final PrimitiveEvent event) {
      if (sessionId().equals(session.sessionId())) {
        final Method method = eventMethods.get(event.type());
        if (method != null) {
          try {
            method.invoke(client, (Object[]) super.decode(event.value()));
          } catch (final IllegalAccessException | InvocationTargetException e) {
            log.warn("Failed to handle event", e);
          }
        }
      }
    }
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
    @SuppressWarnings("unchecked")
    public Object invoke(final Object object, final Method method, final Object[] args)
        throws Throwable {
      final OperationId operationId = operations.get(method);
      if (operationId != null) {
        final CompletableFuture future = new CompletableFuture();
        this.future.set(future);
        final byte[] bytes = encode(args);

        if (operationId.type() == OperationType.COMMAND) {
          final long index = operationIndex.incrementAndGet();
          writeFutures.put(index, future);
          final LogOperation operation =
              new LogOperation(session.sessionId(), name, index, operationId, bytes);
          session
              .producer()
              .append(encodeInternal(operation))
              .whenCompleteAsync(
                  (result, error) -> {
                    if (error == null) {
                      lastIndex = result;
                    }
                  },
                  context());
        } else {
          context()
              .execute(
                  () -> {
                    if (currentIndex >= lastIndex) {
                      final SessionId sessionId = session.sessionId();
                      final Session session = getOrCreateSession(sessionId);
                      final byte[] output =
                          service.apply(
                              new DefaultCommit<>(
                                  currentIndex, operationId, bytes, session, currentTimestamp));
                      future.complete(decode(output));
                      lastIndex = currentIndex;
                    } else {
                      pendingReads.add(new PendingRead(lastIndex, operationId, bytes, future));
                    }
                  });
        }
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
