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

import com.google.common.collect.Maps;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveException;
import io.atomix.primitive.PrimitiveId;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.operation.OperationType;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.service.ServiceContext;
import io.atomix.primitive.service.impl.DefaultCommit;
import io.atomix.primitive.session.Session;
import io.atomix.primitive.session.SessionId;
import io.atomix.utils.concurrent.Scheduled;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.time.LogicalClock;
import io.atomix.utils.time.LogicalTimestamp;
import io.atomix.utils.time.WallClock;
import io.atomix.utils.time.WallClockTimestamp;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Test protocol service. */
public class TestProtocolService {
  private final PartitionId partition;
  private final String name;
  private final PrimitiveType primitiveType;
  private final ServiceConfig config;
  private final PrimitiveService service;
  private final TestProtocolServiceRegistry registry;
  private final ThreadContext context;
  private final Scheduled clock;
  private final Map<SessionId, TestProtocolSession> sessions = Maps.newHashMap();
  private long timestamp;
  private final AtomicLong index = new AtomicLong();
  private Session session;
  private OperationType operationType;
  private boolean open;

  TestProtocolService(
      final PartitionId partition,
      final String name,
      final PrimitiveType primitiveType,
      final ServiceConfig config,
      final PrimitiveService service,
      final TestProtocolServiceRegistry registry,
      final ThreadContext context) {
    this.partition = partition;
    this.name = name;
    this.primitiveType = primitiveType;
    this.config = config;
    this.service = service;
    this.registry = registry;
    this.context = context;
    this.clock = context.schedule(Duration.ofMillis(100), Duration.ofMillis(100), this::tick);
    open();
  }

  /** Opens the service. */
  private void open() {
    open = true;
    service.init(new Context());
  }

  /** Increments the service clock. */
  private void tick() {
    service.tick(new WallClockTimestamp(timestamp()));
  }

  /**
   * Returns the current timestamp.
   *
   * @return the current timestamp
   */
  private long timestamp() {
    timestamp = Math.max(timestamp, System.currentTimeMillis());
    return timestamp;
  }

  /**
   * Opens the given session.
   *
   * @param sessionId the session identifier
   * @param client the session client
   * @return a future to be completed once the session has been opened
   */
  public CompletableFuture<Void> open(final SessionId sessionId, final TestSessionClient client) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    context.execute(
        () -> {
          if (!open) {
            future.completeExceptionally(new PrimitiveException.UnknownService());
            return;
          }

          final TestProtocolSession session =
              new TestProtocolSession(
                  sessionId,
                  name,
                  primitiveType,
                  MemberId.from("test"),
                  service.serializer(),
                  client,
                  context);
          if (sessions.putIfAbsent(sessionId, session) == null) {
            session.setState(Session.State.OPEN);
            service.register(session);
          }
          future.complete(null);
        });
    return future;
  }

  /**
   * Closes the given session.
   *
   * @param sessionId the session identifier
   * @return a future to be completed once the session has been closed
   */
  public CompletableFuture<Void> close(final SessionId sessionId) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    context.execute(
        () -> {
          final TestProtocolSession session = sessions.remove(sessionId);
          if (session != null) {
            session.setState(Session.State.CLOSED);
            service.close(sessionId);
            future.complete(null);
          } else {
            future.completeExceptionally(new PrimitiveException.UnknownSession());
          }
        });
    return future;
  }

  /**
   * Expires the given session.
   *
   * @param sessionId the session identifier
   * @return a future to be completed once the session has been expired
   */
  public CompletableFuture<Void> expire(final SessionId sessionId) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    context.execute(
        () -> {
          final TestProtocolSession session = sessions.remove(sessionId);
          if (session != null) {
            session.setState(Session.State.EXPIRED);
            service.expire(sessionId);
            future.complete(null);
          } else {
            future.completeExceptionally(new PrimitiveException.UnknownSession());
          }
        });
    return future;
  }

  /**
   * Executes the given operation.
   *
   * @param sessionId the session performing the operation
   * @param operation the operation to execute
   * @return a future to be completed with the operation result
   */
  public CompletableFuture<byte[]> execute(
      final SessionId sessionId, final PrimitiveOperation operation) {
    final CompletableFuture<byte[]> future = new CompletableFuture<>();
    context.execute(
        () -> {
          final Session session = sessions.get(sessionId);
          if (session == null) {
            future.completeExceptionally(new PrimitiveException.UnknownSession());
          }

          final long timestamp = timestamp();
          this.session = session;
          this.operationType = operation.id().type();
          service.tick(new WallClockTimestamp(timestamp));

          try {
            final byte[] result =
                service.apply(
                    new DefaultCommit<>(
                        index.incrementAndGet(),
                        operation.id(),
                        operation.value(),
                        session,
                        timestamp));
            future.complete(result);
          } catch (final Exception e) {
            future.completeExceptionally(new PrimitiveException.ServiceException(e.getMessage()));
          }
        });
    return future;
  }

  /**
   * Deletes the test service.
   *
   * @return a future to be completed once the service has been deleted
   */
  public CompletableFuture<Void> delete() {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    context.execute(
        () -> {
          open = false;
          registry.removeService(partition, name);
          service.close();
          clock.cancel();
          future.complete(null);
        });
    return future;
  }

  /** Test service context. */
  private class Context implements ServiceContext {
    @Override
    public PrimitiveId serviceId() {
      return PrimitiveId.from(1);
    }

    @Override
    public String serviceName() {
      return name;
    }

    @Override
    public PrimitiveType serviceType() {
      return primitiveType;
    }

    @Override
    public MemberId localMemberId() {
      return MemberId.from("test");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends ServiceConfig> C serviceConfig() {
      return (C) config;
    }

    @Override
    public long currentIndex() {
      return index.get();
    }

    @Override
    public Session currentSession() {
      return session;
    }

    @Override
    public OperationType currentOperation() {
      return operationType;
    }

    @Override
    public LogicalClock logicalClock() {
      return new LogicalClock() {
        @Override
        public LogicalTimestamp getTime() {
          return new LogicalTimestamp(index.get());
        }
      };
    }

    @Override
    public WallClock wallClock() {
      return new WallClock() {
        @Override
        public WallClockTimestamp getTime() {
          return new WallClockTimestamp(timestamp());
        }
      };
    }
  }
}
