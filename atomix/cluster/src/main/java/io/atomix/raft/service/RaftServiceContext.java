/*
 * Copyright 2015-present Open Networking Foundation
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

package io.atomix.raft.service;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveId;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.operation.OperationType;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.service.ServiceContext;
import io.atomix.primitive.session.Session;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.session.RaftSession;
import io.atomix.raft.session.RaftSessionRegistry;
import io.atomix.utils.concurrent.ThreadContextFactory;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import io.atomix.utils.serializer.Serializer;
import io.atomix.utils.time.LogicalClock;
import io.atomix.utils.time.LogicalTimestamp;
import io.atomix.utils.time.WallClock;
import io.atomix.utils.time.WallClockTimestamp;
import org.slf4j.Logger;

/** Raft server state machine executor. */
public class RaftServiceContext implements ServiceContext {

  private final Logger log;
  private final PrimitiveId primitiveId;
  private final String serviceName;
  private final PrimitiveType primitiveType;
  private final ServiceConfig config;
  private final PrimitiveService service;
  private final RaftContext raft;
  private final RaftSessionRegistry sessions;
  private final ThreadContextFactory threadContextFactory;
  private long currentIndex;
  private final LogicalClock logicalClock =
      new LogicalClock() {
        @Override
        public LogicalTimestamp getTime() {
          return new LogicalTimestamp(currentIndex);
        }
      };
  private Session currentSession;
  private long currentTimestamp;
  private final WallClock wallClock =
      new WallClock() {
        @Override
        public WallClockTimestamp getTime() {
          return new WallClockTimestamp(currentTimestamp);
        }
      };
  private long timestampDelta;
  private OperationType currentOperation;
  private boolean deleted;

  public RaftServiceContext(
      final PrimitiveId primitiveId,
      final String serviceName,
      final PrimitiveType primitiveType,
      final ServiceConfig config,
      final PrimitiveService service,
      final RaftContext raft,
      final ThreadContextFactory threadContextFactory) {
    this.primitiveId = checkNotNull(primitiveId);
    this.serviceName = checkNotNull(serviceName);
    this.primitiveType = checkNotNull(primitiveType);
    this.config = checkNotNull(config);
    this.service = checkNotNull(service);
    this.raft = checkNotNull(raft);
    this.sessions = raft.getSessions();
    this.threadContextFactory = threadContextFactory;
    this.log =
        ContextualLoggerFactory.getLogger(
            getClass(),
            LoggerContext.builder(PrimitiveService.class)
                .addValue(primitiveId)
                .add("type", primitiveType)
                .add("name", serviceName)
                .build());
    service.init(this);
  }

  /**
   * Returns a boolean indicating whether the service has been deleted.
   *
   * @return indicates whether the service has been deleted
   */
  public boolean deleted() {
    return deleted;
  }

  public Serializer serializer() {
    return service.serializer();
  }

  /**
   * Registers the given session.
   *
   * @param index The index of the registration.
   * @param timestamp The timestamp of the registration.
   * @param session The session to register.
   */
  public long openSession(final long index, final long timestamp, final RaftSession session) {
    log.debug("Opening session {}", session.sessionId());

    // Update the state machine index/timestamp.
    tick(index, timestamp);

    // Set the session timestamp to the current service timestamp.
    session.setLastUpdated(currentTimestamp);

    // Expire sessions that have timed out.
    expireSessions(currentTimestamp);

    // Add the session to the sessions list.
    session.open();
    service.register(sessions.addSession(session));

    // Commit the index, causing events to be sent to clients if necessary.
    commit();

    // Complete the future.
    return session.sessionId().id();
  }

  /** Executes scheduled callbacks based on the provided time. */
  private void tick(final long index, final long timestamp) {
    this.currentIndex = index;

    // If the entry timestamp is less than the current state machine timestamp
    // and the delta is not yet set, set the delta and do not change the current timestamp.
    // If the entry timestamp is less than the current state machine timestamp
    // and the delta is set, update the current timestamp to the entry timestamp plus the delta.
    // If the entry timestamp is greater than or equal to the current timestamp, update the current
    // timestamp and reset the delta.
    if (timestamp < currentTimestamp) {
      if (timestampDelta == 0) {
        timestampDelta = currentTimestamp - timestamp;
      } else {
        currentTimestamp = timestamp + timestampDelta;
      }
    } else {
      currentTimestamp = timestamp;
      timestampDelta = 0;
    }

    // Set the current operation type to COMMAND to allow events to be sent.
    setOperation(OperationType.COMMAND);

    service.tick(WallClockTimestamp.from(timestamp));
  }

  /**
   * Sets the current state machine operation type.
   *
   * @param operation the current state machine operation type
   */
  private void setOperation(final OperationType operation) {
    this.currentOperation = operation;
  }

  /** Expires sessions that have timed out. */
  private void expireSessions(final long timestamp) {
    // Iterate through registered sessions.
    for (RaftSession session : sessions.getSessions(primitiveId)) {
      if (session.isTimedOut(timestamp)) {
        log.debug(
            "Session expired in {} milliseconds: {}",
            timestamp - session.getLastUpdated(),
            session);
        session = sessions.removeSession(session.sessionId());
        if (session != null) {
          session.expire();
          service.expire(session.sessionId());
        }
      }
    }
  }

  /** Commits the application of a command to the state machine. */
  @SuppressWarnings("unchecked")
  private void commit() {
    final long index = this.currentIndex;
    for (final RaftSession session : sessions.getSessions(primitiveId)) {
      session.commit(index);
    }
  }

  /**
   * Keeps the given session alive.
   *
   * @param index The index of the keep-alive.
   * @param timestamp The timestamp of the keep-alive.
   * @param session The session to keep-alive.
   * @param commandSequence The session command sequence number.
   * @param eventIndex The session event index.
   */
  public boolean keepAlive(
      final long index,
      final long timestamp,
      final RaftSession session,
      final long commandSequence,
      final long eventIndex) {
    // If the service has been deleted, just return false to ignore the keep-alive.
    if (deleted) {
      return false;
    }

    // Update the state machine index/timestamp.
    tick(index, timestamp);

    // The session may have been closed by the time this update was executed on the service thread.
    if (session.getState() != Session.State.CLOSED) {
      // Update the session's timestamp to prevent it from being expired.
      session.setLastUpdated(timestamp);

      // Clear results cached in the session.
      session.clearResults(commandSequence);

      // Resend missing events starting from the last received event index.
      session.resendEvents(eventIndex);

      // Update the session's request sequence number. The command sequence number will be applied
      // iff the existing request sequence number is less than the command sequence number. This
      // must
      // be applied to ensure that request sequence numbers are reset after a leader change since
      // leaders
      // track request sequence numbers in local memory.
      session.resetRequestSequence(commandSequence);

      // Update the sessions' command sequence number. The command sequence number will be applied
      // iff the existing sequence number is less than the keep-alive command sequence number. This
      // should
      // not be the case under normal operation since the command sequence number in keep-alive
      // requests
      // represents the highest sequence for which a client has received a response (the command has
      // already
      // been completed), but since the log compaction algorithm can exclude individual entries from
      // replication,
      // the command sequence number must be applied for keep-alive requests to reset the sequence
      // number in
      // the event the last command for the session was cleaned/compacted from the log.
      session.setCommandSequence(commandSequence);

      // Complete the future.
      return true;
    } else {
      return false;
    }
  }

  /**
   * Unregister the given session.
   *
   * @param index The index of the unregister.
   * @param timestamp The timestamp of the unregister.
   * @param session The session to unregister.
   * @param expired Whether the session was expired by the leader.
   */
  public void closeSession(
      final long index, final long timestamp, RaftSession session, final boolean expired) {
    log.debug("Closing session {}", session.sessionId());

    // Update the session's timestamp to prevent it from being expired.
    session.setLastUpdated(timestamp);

    // Update the state machine index/timestamp.
    tick(index, timestamp);

    // Expire sessions that have timed out.
    expireSessions(currentTimestamp);

    // Remove the session from the sessions list.
    if (expired) {
      session = sessions.removeSession(session.sessionId());
      if (session != null) {
        session.expire();
        service.expire(session.sessionId());
      }
    } else {
      session = sessions.removeSession(session.sessionId());
      if (session != null) {
        session.close();
        service.close(session.sessionId());
      }
    }

    // Commit the index, causing events to be sent to clients if necessary.
    commit();
  }

  /** Closes the service context. */
  public void close() {
    for (final RaftSession serviceSession : sessions.getSessions(serviceId())) {
      sessions.removeSession(serviceSession.sessionId());
      serviceSession.close();
      service.close(serviceSession.sessionId());
    }
    service.close();
    deleted = true;
  }

  @Override
  public PrimitiveId serviceId() {
    return primitiveId;
  }

  @Override
  public String serviceName() {
    return serviceName;
  }

  @Override
  public PrimitiveType serviceType() {
    return primitiveType;
  }

  @Override
  public MemberId localMemberId() {
    return raft.localMemberId();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <C extends ServiceConfig> C serviceConfig() {
    return (C) config;
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
    return logicalClock;
  }

  @Override
  public WallClock wallClock() {
    return wallClock;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("server", raft.getName())
        .add("type", primitiveType)
        .add("name", serviceName)
        .add("id", primitiveId)
        .toString();
  }
}
