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
package io.atomix.primitive.service;

import com.google.common.collect.Maps;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveException;
import io.atomix.primitive.PrimitiveId;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.operation.OperationId;
import io.atomix.primitive.operation.Operations;
import io.atomix.primitive.service.impl.DefaultServiceExecutor;
import io.atomix.primitive.session.Session;
import io.atomix.primitive.session.SessionId;
import io.atomix.primitive.session.impl.ClientSession;
import io.atomix.utils.concurrent.Scheduler;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import io.atomix.utils.serializer.Serializer;
import io.atomix.utils.time.Clock;
import io.atomix.utils.time.LogicalClock;
import io.atomix.utils.time.WallClock;
import io.atomix.utils.time.WallClockTimestamp;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;

/** Raft service. */
public abstract class AbstractPrimitiveService<C> implements PrimitiveService {
  private final PrimitiveType primitiveType;
  private final Class<C> clientInterface;
  private final Serializer serializer;
  private Logger log;
  private ServiceContext context;
  private ServiceExecutor executor;
  private final Map<SessionId, Session<C>> sessions = Maps.newHashMap();

  protected AbstractPrimitiveService(final PrimitiveType primitiveType) {
    this(primitiveType, null);
  }

  protected AbstractPrimitiveService(
      final PrimitiveType primitiveType, final Class<C> clientInterface) {
    this.primitiveType = primitiveType;
    this.clientInterface = clientInterface;
    this.serializer = Serializer.using(primitiveType.namespace());
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

  @Override
  public final void init(final ServiceContext context) {
    this.context = context;
    this.executor = new DefaultServiceExecutor(context, serializer());
    this.log =
        ContextualLoggerFactory.getLogger(
            getClass(),
            LoggerContext.builder(PrimitiveService.class)
                .addValue(context.serviceId())
                .add("type", context.serviceType())
                .add("name", context.serviceName())
                .build());
    configure(executor);
  }

  @Override
  public final void tick(final WallClockTimestamp timestamp) {
    executor.tick(timestamp);
  }

  @Override
  public Serializer serializer() {
    return serializer;
  }

  @Override
  public final byte[] apply(final Commit<byte[]> commit) {
    return executor.apply(commit);
  }

  @Override
  @SuppressWarnings("unchecked")
  public final void register(final Session session) {
    sessions.put(session.sessionId(), new ClientSession<>(clientInterface, session));
    onOpen(session);
  }

  @Override
  public final void expire(final SessionId sessionId) {
    final Session session = sessions.remove(sessionId);
    if (session != null) {
      onExpire(session);
    }
  }

  @Override
  public final void close(final SessionId sessionId) {
    final Session session = sessions.remove(sessionId);
    if (session != null) {
      onClose(session);
    }
  }

  /**
   * Configures the state machine.
   *
   * <p>By default, this method will configure state machine operations by extracting public methods
   * with a single {@link Commit} parameter via reflection. Override this method to explicitly
   * register state machine operations via the provided {@link ServiceExecutor}.
   *
   * @param executor The state machine executor.
   */
  protected void configure(final ServiceExecutor executor) {
    Operations.getOperationMap(getClass())
        .forEach(((operationId, method) -> configure(operationId, method, executor)));
  }

  /**
   * Configures the given operation on the given executor.
   *
   * @param operationId the operation identifier
   * @param method the operation method
   * @param executor the service executor
   */
  private void configure(
      final OperationId operationId, final Method method, final ServiceExecutor executor) {
    if (method.getReturnType() == Void.TYPE) {
      if (method.getParameterTypes().length == 0) {
        executor.register(
            operationId,
            () -> {
              try {
                method.invoke(this);
              } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new PrimitiveException.ServiceException(e);
              }
            });
      } else {
        executor.register(
            operationId,
            args -> {
              try {
                method.invoke(this, (Object[]) args.value());
              } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new PrimitiveException.ServiceException(e);
              }
            });
      }
    } else {
      if (method.getParameterTypes().length == 0) {
        executor.register(
            operationId,
            () -> {
              try {
                return method.invoke(this);
              } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new PrimitiveException.ServiceException(e);
              }
            });
      } else {
        executor.register(
            operationId,
            args -> {
              try {
                return method.invoke(this, (Object[]) args.value());
              } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new PrimitiveException.ServiceException(e);
              }
            });
      }
    }
  }

  /**
   * Returns the primitive type.
   *
   * @return the primitive type
   */
  protected PrimitiveType getPrimitiveType() {
    return primitiveType;
  }

  /**
   * Returns the service logger.
   *
   * @return the service logger
   */
  protected Logger getLogger() {
    return log;
  }

  /**
   * Returns the state machine scheduler.
   *
   * @return The state machine scheduler.
   */
  protected Scheduler getScheduler() {
    return executor;
  }

  /**
   * Returns the ID of the cluster member this service instance is running on. Caution: This
   * information should not be used in anyway to modify the machine's state, as it could be used to
   * violate the invariant that all instances of a partition must have the same state. However, it
   * can be used safely for logging purposes or for generating meaningful filenames for instance
   * (this can be useful especially in the case where several cluster members are run on the same
   * host).
   *
   * @return The local member ID
   */
  protected MemberId getLocalMemberId() {
    return context.localMemberId();
  }

  /**
   * Returns the unique state machine identifier.
   *
   * @return The unique state machine identifier.
   */
  protected PrimitiveId getServiceId() {
    return context.serviceId();
  }

  /**
   * Returns the unique state machine name.
   *
   * @return The unique state machine name.
   */
  protected String getServiceName() {
    return context.serviceName();
  }

  /**
   * Returns the state machine's current index.
   *
   * @return The state machine's current index.
   */
  protected long getCurrentIndex() {
    return context.currentIndex();
  }

  /**
   * Returns the current session.
   *
   * @return the current session
   */
  protected Session<C> getCurrentSession() {
    return getSession(context.currentSession().sessionId());
  }

  /**
   * Returns the state machine's clock.
   *
   * @return The state machine's clock.
   */
  protected Clock getClock() {
    return getWallClock();
  }

  /**
   * Returns the state machine's wall clock.
   *
   * @return The state machine's wall clock.
   */
  protected WallClock getWallClock() {
    return context.wallClock();
  }

  /**
   * Returns the state machine's logical clock.
   *
   * @return The state machine's logical clock.
   */
  protected LogicalClock getLogicalClock() {
    return context.logicalClock();
  }

  /**
   * Returns the session with the given identifier.
   *
   * @param sessionId the session identifier
   * @return the primitive session
   */
  protected Session<C> getSession(final long sessionId) {
    return getSession(SessionId.from(sessionId));
  }

  /**
   * Returns the session with the given identifier.
   *
   * @param sessionId the session identifier
   * @return the primitive session
   */
  protected Session<C> getSession(final SessionId sessionId) {
    return sessions.get(sessionId);
  }

  /**
   * Returns the collection of open sessions.
   *
   * @return the collection of open sessions
   */
  protected Collection<Session<C>> getSessions() {
    return sessions.values();
  }

  /**
   * Called when a new session is registered.
   *
   * <p>A session is registered when a new client connects to the cluster or an existing client
   * recovers its session after being partitioned from the cluster. It's important to note that when
   * this method is called, the {@link Session} is <em>not yet open</em> and so events cannot be
   * {@link Session#accept(Consumer) published} to the registered session. This is because clients
   * cannot reliably track messages pushed from server state machines to the client until the
   * session has been fully registered. Session event messages may still be published to other
   * already-registered sessions in reaction to a session being registered.
   *
   * <p>To push session event messages to a client through its session upon registration, state
   * machines can use an asynchronous callback or schedule a callback to send a message.
   *
   * <pre>{@code
   * public void onOpen(RaftSession session) {
   *   executor.execute(() -> session.publish("foo", "Hello world!"));
   * }
   *
   * }</pre>
   *
   * Sending a session event message in an asynchronous callback allows the server time to register
   * the session and notify the client before the event message is sent. Published event messages
   * sent via this method will be sent the next time an operation is applied to the state machine.
   *
   * @param session The session that was registered
   */
  protected void onOpen(final Session session) {}

  /**
   * Called when a session is expired by the system.
   *
   * <p>This method is called when a client fails to keep its session alive with the cluster. If the
   * leader hasn't heard from a client for a configurable time interval, the leader will expire the
   * session to free the related memory. This method will always be called for a given session
   * before {@link #onClose(Session)}, and {@link #onClose(Session)} will always be called following
   * this method.
   *
   * @param session The session that was expired
   */
  protected void onExpire(final Session session) {}

  /**
   * Called when a session was closed by the client.
   *
   * <p>This method is called when a client explicitly closes a session.
   *
   * @param session The session that was closed
   */
  protected void onClose(final Session session) {}
}
