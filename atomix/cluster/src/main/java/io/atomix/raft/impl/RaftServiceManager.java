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
package io.atomix.raft.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveId;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.primitive.service.ServiceConfig;
import io.atomix.primitive.session.SessionId;
import io.atomix.primitive.session.SessionMetadata;
import io.atomix.raft.RaftException;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftStateMachine;
import io.atomix.raft.metrics.RaftServiceMetrics;
import io.atomix.raft.service.RaftServiceContext;
import io.atomix.raft.session.RaftSession;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.CloseSessionEntry;
import io.atomix.raft.storage.log.entry.CommandEntry;
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.raft.storage.log.entry.InitializeEntry;
import io.atomix.raft.storage.log.entry.KeepAliveEntry;
import io.atomix.raft.storage.log.entry.MetadataEntry;
import io.atomix.raft.storage.log.entry.OpenSessionEntry;
import io.atomix.raft.storage.log.entry.QueryEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.snapshot.Snapshot;
import io.atomix.raft.storage.snapshot.impl.SnapshotReader;
import io.atomix.raft.storage.snapshot.impl.SnapshotWriter;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.atomix.utils.AtomixIOException;
import io.atomix.utils.concurrent.ComposableFuture;
import io.atomix.utils.concurrent.Futures;
import io.atomix.utils.concurrent.OrderedFuture;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.concurrent.ThreadContextFactory;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import io.atomix.utils.serializer.Serializer;
import io.atomix.utils.time.WallClockTimestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;

/**
 * Internal server state machine.
 *
 * <p>The internal state machine handles application of commands to the user provided {@link
 * PrimitiveService} and keeps track of internal state like sessions and the various indexes
 * relevant to log compaction.
 */
public class RaftServiceManager implements RaftStateMachine {

  private static final Duration SNAPSHOT_INTERVAL = Duration.ofSeconds(10);
  private static final Duration SNAPSHOT_COMPLETION_DELAY = Duration.ofSeconds(10);
  private static final Duration COMPACT_DELAY = Duration.ofSeconds(10);

  private static final int SEGMENT_BUFFER_FACTOR = 5;
  private static final String SNAPSHOT_FAILURE_ERROR_MESSAGE =
      "Expected to read snapshot, but caught a non recoverable error; blocking thread to avoid applying further entries and creating inconsistencies";

  private final Logger logger;
  private final RaftContext raft;
  private final ThreadContext stateContext;
  private final ThreadContextFactory threadContextFactory;
  private final RaftLog log;
  private final RaftLogReader reader;
  private final Map<Long, CompletableFuture> futures = Maps.newHashMap();
  private final RaftServiceMetrics metrics;
  private volatile CompletableFuture<Void> compactFuture;
  private long lastEnqueued;
  private long lastCompacted;

  public RaftServiceManager(
      final RaftContext raft,
      final ThreadContext stateContext,
      final ThreadContextFactory threadContextFactory) {
    this.raft = checkNotNull(raft, "state cannot be null");
    this.log = raft.getLog();
    this.reader = log.openReader(1, RaftLogReader.Mode.COMMITS);
    this.stateContext = stateContext;
    this.threadContextFactory = threadContextFactory;
    this.logger =
        ContextualLoggerFactory.getLogger(
            getClass(), LoggerContext.builder(RaftServer.class).addValue(raft.getName()).build());
    this.lastEnqueued = reader.getFirstIndex() - 1;
    this.metrics = new RaftServiceMetrics(raft.getName());
    scheduleSnapshots();
  }

  /**
   * Returns the service thread context.
   *
   * @return the service thread context
   */
  @Override
  public ThreadContext executor() {
    return stateContext;
  }

  /**
   * Compacts Raft logs.
   *
   * @return a future to be completed once logs have been compacted
   */
  @Override
  public CompletableFuture<Void> compact() {
    return takeSnapshots(false, true);
  }

  /**
   * Applies all commits up to the given index.
   *
   * <p>Calls to this method are assumed not to expect a result. This allows some optimizations to
   * be made internally since linearizable events don't have to be waited to complete the command.
   *
   * @param index The index up to which to apply commits.
   */
  @Override
  public void applyAll(final long index) {
    enqueueBatch(index);
  }

  /**
   * Applies the entry at the given index to the state machine.
   *
   * <p>Calls to this method are assumed to expect a result. This means linearizable session events
   * triggered by the application of the command at the given index will be awaited before
   * completing the returned future.
   *
   * @param index The index to apply.
   * @return A completable future to be completed once the commit has been applied.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<T> apply(final long index) {
    final CompletableFuture<T> future =
        futures.computeIfAbsent(index, i -> new CompletableFuture<T>());
    enqueueBatch(index);
    return future;
  }

  /**
   * Applies an entry to the state machine.
   *
   * <p>Calls to this method are assumed to expect a result. This means linearizable session events
   * triggered by the application of the given entry will be awaited before completing the returned
   * future.
   *
   * @param entry The entry to apply.
   * @return A completable future to be completed with the result.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<T> apply(final Indexed<? extends RaftLogEntry> entry) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    raft.notifyCommitListeners(entry);

    stateContext.execute(
        () -> {
          logger.trace("Applying {}", entry);
          try {
            if (entry.type() == QueryEntry.class) {
              applyQuery(entry.cast())
                  .whenComplete(
                      (r, e) -> {
                        if (e != null) {
                          future.completeExceptionally(e);
                        } else {
                          future.complete((T) r);
                        }
                      });
            } else {
              // Get the current snapshot. If the snapshot is for a higher index then skip this
              // operation.
              // If the snapshot is for the prior index, install it.
              final Snapshot snapshot = raft.getSnapshotStore().getCurrentSnapshot();
              if (snapshot != null) {
                if (snapshot.index() >= entry.index()) {
                  future.complete(null);
                  return;
                } else if (snapshot.index() == entry.index() - 1) {
                  install(snapshot);
                }
              }

              if (entry.type() == CommandEntry.class) {
                future.complete((T) applyCommand(entry.cast()));
              } else if (entry.type() == OpenSessionEntry.class) {
                future.complete((T) (Long) applyOpenSession(entry.cast()));
              } else if (entry.type() == KeepAliveEntry.class) {
                future.complete((T) applyKeepAlive(entry.cast()));
              } else if (entry.type() == CloseSessionEntry.class) {
                applyCloseSession(entry.cast());
                future.complete(null);
              } else if (entry.type() == MetadataEntry.class) {
                future.complete((T) applyMetadata(entry.cast()));
              } else if (entry.type() == InitializeEntry.class) {
                future.complete((T) applyInitialize(entry.cast()));
              } else if (entry.type() == ConfigurationEntry.class) {
                future.complete((T) applyConfiguration(entry.cast()));
              } else if (entry.type() == ZeebeEntry.class) {
                future.complete(null);
              } else {
                future.completeExceptionally(
                    new RaftException.ProtocolException("Unknown entry type"));
              }
            }
          } catch (final Exception e) {
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  @Override
  public void close() {
    // Don't close the thread context here since state machines can be reused.
  }

  @Override
  public long getCompactableIndex() {
    return raft.getLastApplied();
  }

  @Override
  public long getCompactableTerm() {
    return raft.getLastAppliedTerm();
  }

  /**
   * Prepares sessions for the given index.
   *
   * @param snapshot the snapshot to install
   */
  void install(final Snapshot snapshot) {
    logger.debug("Installing snapshot {}", snapshot);
    try (final SnapshotReader reader = snapshot.openReader()) {
      while (reader.hasRemaining()) {
        final int length = reader.readInt();
        if (length > 0) {
          final SnapshotReader serviceReader =
              new SnapshotReader(reader.buffer().slice(length), reader.snapshot());
          installService(serviceReader);
          reader.skip(length);
        }
      }
    } catch (final Exception e) {
      logger.error(SNAPSHOT_FAILURE_ERROR_MESSAGE, e);

      // block the current thread to avoid an inconsistent state
      // TODO (saig0): find a way to recover from an install snapshot failure
      while (true) {
        try {
          new CountDownLatch(1).await();
        } catch (final InterruptedException ex) {
          // still blocking
        }
      }
    }
  }

  /**
   * Restores the service associated with the given snapshot.
   *
   * @param reader the snapshot reader
   */
  private void installService(final SnapshotReader reader) {
    final PrimitiveId primitiveId = PrimitiveId.from(reader.readLong());
    final PrimitiveType primitiveType =
        raft.getPrimitiveTypes().getPrimitiveType(reader.readString());
    final String serviceName = reader.readString();
    final byte[] serviceConfig = reader.readBytes(reader.readInt());

    // Get or create the service associated with the snapshot.
    logger.debug("Installing service {} {}", primitiveId, serviceName);
    final RaftServiceContext service =
        initializeService(primitiveId, primitiveType, serviceName, serviceConfig);
    try {
      service.installSnapshot(reader);
    } catch (final Exception e) {
      logger.error("Failed to install snapshot for service {}", serviceName, e);
      throw e;
    }
  }

  /** Initializes a new service. */
  @SuppressWarnings("unchecked")
  private RaftServiceContext initializeService(
      final PrimitiveId primitiveId,
      final PrimitiveType primitiveType,
      final String serviceName,
      final byte[] config) {
    final RaftServiceContext oldService = raft.getServices().getService(serviceName);
    final ServiceConfig serviceConfig =
        config == null
            ? new ServiceConfig()
            : Serializer.using(primitiveType.namespace()).decode(config);
    final RaftServiceContext service =
        new RaftServiceContext(
            primitiveId,
            serviceName,
            primitiveType,
            serviceConfig,
            primitiveType.newService(serviceConfig),
            raft,
            threadContextFactory);
    raft.getServices().registerService(service);

    // If a service with this name was already registered, remove all of its sessions.
    if (oldService != null) {
      oldService.close();
    }
    return service;
  }

  /**
   * Applies an initialize entry.
   *
   * <p>Initialize entries are used only at the beginning of a new leader's term to force the
   * commitment of entries from prior terms, therefore no logic needs to take place.
   */
  private CompletableFuture<Void> applyInitialize(final Indexed<InitializeEntry> entry) {
    for (final RaftServiceContext service : raft.getServices()) {
      service.keepAliveSessions(entry.index(), entry.entry().timestamp());
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Applies a configuration entry to the internal state machine.
   *
   * <p>Configuration entries are applied to internal server state when written to the log. Thus, no
   * significant logic needs to take place in the handling of configuration entries. We simply
   * release the previous configuration entry since it was overwritten by a more recent committed
   * configuration entry.
   */
  private CompletableFuture<Void> applyConfiguration(final Indexed<ConfigurationEntry> entry) {
    for (final RaftServiceContext service : raft.getServices()) {
      service.keepAliveSessions(entry.index(), entry.entry().timestamp());
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Applies a session keep alive entry to the state machine.
   *
   * <p>Keep alive entries are applied to the internal state machine to reset the timeout for a
   * specific session. If the session indicated by the KeepAliveEntry is still held in memory, we
   * mark the session as trusted, indicating that the client has committed a keep alive within the
   * required timeout. Additionally, we check all other sessions for expiration based on the
   * timestamp provided by this KeepAliveEntry. Note that sessions are never completely expired via
   * this method. Leaders must explicitly commit an UnregisterEntry to expire a session.
   *
   * <p>When a KeepAliveEntry is committed to the internal state machine, two specific fields
   * provided in the entry are used to update server-side session state. The {@code commandSequence}
   * indicates the highest command for which the session has received a successful response in the
   * proper sequence. By applying the {@code commandSequence} to the server session, we clear
   * command output held in memory up to that point. The {@code eventVersion} indicates the index up
   * to which the client has received event messages in sequence for the session. Applying the
   * {@code eventVersion} to the server-side session results in events up to that index being
   * removed from memory as they were acknowledged by the client. It's essential that both of these
   * fields be applied via entries committed to the Raft log to ensure they're applied on all
   * servers in sequential order.
   *
   * <p>Keep alive entries are retained in the log until the next time the client sends a keep alive
   * entry or until the client's session is expired. This ensures for sessions that have long
   * timeouts, keep alive entries cannot be cleaned from the log before they're replicated to some
   * servers.
   */
  private long[] applyKeepAlive(final Indexed<KeepAliveEntry> entry) {

    // Store the session/command/event sequence and event index instead of acquiring a reference to
    // the entry.
    final long[] sessionIds = entry.entry().sessionIds();
    final long[] commandSequences = entry.entry().commandSequenceNumbers();
    final long[] eventIndexes = entry.entry().eventIndexes();

    // Iterate through session identifiers and keep sessions alive.
    final List<Long> successfulSessionIds = new ArrayList<>(sessionIds.length);
    final Set<RaftServiceContext> services = new HashSet<>();
    for (int i = 0; i < sessionIds.length; i++) {
      final long sessionId = sessionIds[i];
      final long commandSequence = commandSequences[i];
      final long eventIndex = eventIndexes[i];

      final RaftSession session = raft.getSessions().getSession(sessionId);
      if (session != null) {
        if (session
            .getService()
            .keepAlive(
                entry.index(), entry.entry().timestamp(), session, commandSequence, eventIndex)) {
          successfulSessionIds.add(sessionId);
          services.add(session.getService());
        }
      }
    }

    // Iterate through services and complete keep-alives, causing sessions to be expired if
    // necessary.
    for (final RaftServiceContext service : services) {
      service.completeKeepAlive(entry.index(), entry.entry().timestamp());
    }

    expireOrphanSessions(entry.entry().timestamp());

    return Longs.toArray(successfulSessionIds);
  }

  /** Expires sessions that have timed out. */
  private void expireOrphanSessions(final long timestamp) {
    // Iterate through registered sessions.
    for (RaftSession session : raft.getSessions().getSessions()) {
      if (session.getService().deleted() && session.isTimedOut(timestamp)) {
        logger.debug(
            "Orphaned session expired in {} milliseconds: {}",
            timestamp - session.getLastUpdated(),
            session);
        session = raft.getSessions().removeSession(session.sessionId());
        if (session != null) {
          session.expire();
        }
      }
    }
  }

  /** Applies an open session entry to the state machine. */
  private long applyOpenSession(final Indexed<OpenSessionEntry> entry) {
    final PrimitiveType primitiveType =
        raft.getPrimitiveTypes().getPrimitiveType(entry.entry().serviceType());

    // Get the state machine executor or create one if it doesn't already exist.
    final RaftServiceContext service =
        getOrInitializeService(
            PrimitiveId.from(entry.index()),
            primitiveType,
            entry.entry().serviceName(),
            entry.entry().serviceConfig());

    final SessionId sessionId = SessionId.from(entry.index());
    final RaftSession session =
        raft.getSessions()
            .addSession(
                new RaftSession(
                    sessionId,
                    MemberId.from(entry.entry().memberId()),
                    entry.entry().serviceName(),
                    primitiveType,
                    entry.entry().readConsistency(),
                    entry.entry().minTimeout(),
                    entry.entry().maxTimeout(),
                    entry.entry().timestamp(),
                    service.serializer(),
                    service,
                    raft,
                    threadContextFactory));
    return service.openSession(entry.index(), entry.entry().timestamp(), session);
  }

  /** Gets or initializes a service context. */
  private RaftServiceContext getOrInitializeService(
      final PrimitiveId primitiveId,
      final PrimitiveType primitiveType,
      final String serviceName,
      final byte[] config) {
    // Get the state machine executor or create one if it doesn't already exist.
    RaftServiceContext service = raft.getServices().getService(serviceName);
    if (service == null) {
      service = initializeService(primitiveId, primitiveType, serviceName, config);
    }
    return service;
  }

  /** Applies a close session entry to the state machine. */
  private void applyCloseSession(final Indexed<CloseSessionEntry> entry) {
    final RaftSession session = raft.getSessions().getSession(entry.entry().session());

    // If the server session is null, the session either never existed or already expired.
    if (session == null) {
      throw new RaftException.UnknownSession("Unknown session: " + entry.entry().session());
    }

    final RaftServiceContext service = session.getService();
    service.closeSession(
        entry.index(), entry.entry().timestamp(), session, entry.entry().expired());

    // If this is a delete, unregister the service.
    if (entry.entry().delete()) {
      raft.getServices().unregisterService(service);
      service.close();
    }
  }

  /** Applies a metadata entry to the state machine. */
  private MetadataResult applyMetadata(final Indexed<MetadataEntry> entry) {
    // If the session ID is non-zero, read the metadata for the associated state machine.
    if (entry.entry().session() > 0) {
      final RaftSession session = raft.getSessions().getSession(entry.entry().session());

      // If the session is null, return an UnknownSessionException.
      if (session == null) {
        logger.warn("Unknown session: " + entry.entry().session());
        throw new RaftException.UnknownSession("Unknown session: " + entry.entry().session());
      }

      final Set<SessionMetadata> sessions = new HashSet<>();
      for (final RaftSession s : raft.getSessions().getSessions()) {
        if (s.primitiveName().equals(session.primitiveName())) {
          sessions.add(
              new SessionMetadata(s.sessionId().id(), s.primitiveName(), s.primitiveType().name()));
        }
      }
      return new MetadataResult(sessions);
    } else {
      final Set<SessionMetadata> sessions = new HashSet<>();
      for (final RaftSession session : raft.getSessions().getSessions()) {
        sessions.add(
            new SessionMetadata(
                session.sessionId().id(), session.primitiveName(), session.primitiveType().name()));
      }
      return new MetadataResult(sessions);
    }
  }

  /**
   * Applies a command entry to the state machine.
   *
   * <p>Command entries result in commands being executed on the user provided {@link
   * PrimitiveService} and a response being sent back to the client by completing the returned
   * future. All command responses are cached in the command's {@link RaftSession} for fault
   * tolerance. In the event that the same command is applied to the state machine more than once,
   * the original response will be returned.
   *
   * <p>Command entries are written with a sequence number. The sequence number is used to ensure
   * that commands are applied to the state machine in sequential order. If a command entry has a
   * sequence number that is less than the next sequence number for the session, that indicates that
   * it is a duplicate of a command that was already applied. Otherwise, commands are assumed to
   * have been received in sequential order. The reason for this assumption is because leaders
   * always sequence commands as they're written to the log, so no sequence number will be skipped.
   */
  private OperationResult applyCommand(final Indexed<CommandEntry> entry) {
    // First check to ensure that the session exists.
    final RaftSession session = raft.getSessions().getSession(entry.entry().session());

    // If the session is null, return an UnknownSessionException. Commands applied to the state
    // machine must
    // have a session. We ensure that session register/unregister entries are not compacted from the
    // log
    // until all associated commands have been cleaned.
    // Note that it's possible for a session to be unknown if a later snapshot has been taken, so we
    // don't want
    // to log warnings here.
    if (session == null) {
      logger.debug("Unknown session: " + entry.entry().session());
      throw new RaftException.UnknownSession("unknown session: " + entry.entry().session());
    }

    // Increment the load counter to avoid snapshotting under high load.
    raft.getLoadMonitor().recordEvent();

    // Execute the command using the state machine associated with the session.
    return session
        .getService()
        .executeCommand(
            entry.index(),
            entry.entry().sequenceNumber(),
            entry.entry().timestamp(),
            session,
            entry.entry().operation());
  }

  /**
   * Applies a query entry to the state machine.
   *
   * <p>Query entries are applied to the user {@link PrimitiveService} for read-only operations.
   * Because queries are read-only, they may only be applied on a single server in the cluster, and
   * query entries do not go through the Raft log. Thus, it is critical that measures be taken to
   * ensure clients see a consistent view of the cluster event when switching servers. To do so,
   * clients provide a sequence and version number for each query. The sequence number is the order
   * in which the query was sent by the client. Sequence numbers are shared across both commands and
   * queries. The version number indicates the last index for which the client saw a command or
   * query response. In the event that the lastApplied index of this state machine does not meet the
   * provided version number, we wait for the state machine to catch up before applying the query.
   * This ensures clients see state progress monotonically even when switching servers.
   *
   * <p>Because queries may only be applied on a single server in the cluster they cannot result in
   * the publishing of session events. Events require commands to be written to the Raft log to
   * ensure fault-tolerance and consistency across the cluster.
   */
  private CompletableFuture<OperationResult> applyQuery(final Indexed<QueryEntry> entry) {
    final RaftSession session = raft.getSessions().getSession(entry.entry().session());

    // If the session is null then that indicates that the session already timed out or it never
    // existed.
    // Return with an UnknownSessionException.
    if (session == null) {
      logger.warn("Unknown session: " + entry.entry().session());
      return Futures.exceptionalFuture(
          new RaftException.UnknownSession("unknown session " + entry.entry().session()));
    }

    // Execute the query using the state machine associated with the session.
    return session
        .getService()
        .executeQuery(
            entry.index(),
            entry.entry().sequenceNumber(),
            entry.entry().timestamp(),
            session,
            entry.entry().operation());
  }

  /**
   * Applies all entries up to the given index.
   *
   * @param index the index up to which to apply entries
   */
  private void enqueueBatch(final long index) {
    while (lastEnqueued < index) {
      enqueueIndex(++lastEnqueued);
    }
  }

  /**
   * Enqueues an index to be applied to the state machine.
   *
   * @param index the index to be applied to the state machine
   */
  private void enqueueIndex(final long index) {
    raft.getThreadContext().execute(() -> applyIndex(index));
  }

  /**
   * Applies the next entry in the log up to the given index.
   *
   * @param index the index up to which to apply the entry
   */
  @SuppressWarnings("unchecked")
  private void applyIndex(final long index) {
    // Apply entries prior to this entry.
    if (reader.hasNext() && reader.getNextIndex() == index) {
      // Read the entry from the log. If the entry is non-null then apply it, otherwise
      // simply update the last applied index and return a null result.
      final Indexed<RaftLogEntry> entry = reader.next();
      try {
        if (entry.index() != index) {
          throw new IllegalStateException(
              "inconsistent index applying entry " + index + ": " + entry);
        }
        final CompletableFuture future = futures.remove(index);
        final long term = entry.entry().term();
        apply(entry)
            .whenComplete(
                (r, e) -> {
                  raft.setLastApplied(index, term);
                  if (future != null) {
                    if (e == null) {
                      future.complete(r);
                    } else {
                      future.completeExceptionally(e);
                    }
                  }
                });
      } catch (final Exception e) {
        logger.error("Failed to apply {}: {}", entry, e);
      }
    } else {
      final CompletableFuture future = futures.remove(index);
      if (future != null) {
        logger.error("Cannot apply index " + index);
        future.completeExceptionally(new IndexOutOfBoundsException("Cannot apply index " + index));
      }
    }
  }

  /** Schedules a snapshot iteration. */
  private void scheduleSnapshots() {
    raft.getThreadContext().schedule(getSnapshotInterval(), () -> takeSnapshots(true, false));
  }

  /**
   * Takes a snapshot of all services and compacts logs if the server is not under high load or disk
   * needs to be freed.
   */
  private CompletableFuture<Void> takeSnapshots(
      final boolean rescheduleAfterCompletion, final boolean force) {
    // If compaction is already in progress, return the existing future and reschedule if this is a
    // scheduled compaction.
    if (compactFuture != null) {
      if (rescheduleAfterCompletion) {
        compactFuture.whenComplete((r, e) -> scheduleSnapshots());
      }
      return compactFuture;
    }

    final long compactableIndex = getCompactableIndex();

    // Only take snapshots if segments can be removed from the log below the compactableIndex index.
    if (raft.getLog().isCompactable(compactableIndex)
        && raft.getLog().getCompactableIndex(compactableIndex) > lastCompacted) {

      // Determine whether the node is running out of disk space.
      final boolean runningOutOfDiskSpace = isRunningOutOfDiskSpace();

      // Determine whether the node is running out of memory.
      final boolean runningOutOfMemory = isRunningOutOfMemory();

      // If compaction is not already being forced...
      if (!force
          // And the node isn't running out of memory (we need to free up memory if it is)...
          && !runningOutOfMemory
          // And dynamic compaction is enabled (we need to compact immediately if it's disabled)...
          && raft.getStorage().dynamicCompaction()
          // And the node isn't running out of disk space (we need to compact immediately if it
          // is)...
          && !runningOutOfDiskSpace
          // And the server is under high load (we can skip compaction at this point)...
          && raft.getLoadMonitor().isUnderHighLoad()) {
        // We can skip taking a snapshot for now.
        logger.debug("Skipping compaction due to high load");
        if (rescheduleAfterCompletion) {
          scheduleSnapshots();
        }
        return CompletableFuture.completedFuture(null);
      }

      logger.debug("Snapshotting services");

      // Update the index at which the log was last compacted.
      this.lastCompacted = compactableIndex;

      // We need to ensure that callbacks added to the compaction future are completed in the order
      // in which they
      // were added in order to preserve the order of retries when appending to the log.
      compactFuture = new OrderedFuture<>();

      // Wait for snapshots in all state machines to be completed before compacting the log at the
      // last applied index.
      takeSnapshots()
          .whenComplete(
              (snapshot, error) -> {
                if (error == null) {
                  tryToCompleteSnapshot(snapshot);
                }
              });

      // Reschedule snapshots after completion if necessary.
      if (rescheduleAfterCompletion) {
        compactFuture.whenComplete((r, e) -> scheduleSnapshots());
      }
      return compactFuture;
    }
    // Otherwise, if the log can't be compacted anyways, just reschedule snapshots.
    else {
      if (rescheduleAfterCompletion) {
        scheduleSnapshots();
      }
      return CompletableFuture.completedFuture(null);
    }
  }

  /**
   * Takes and persists snapshots of provided services.
   *
   * @return future to be completed once all snapshots have been completed
   */
  private CompletableFuture<Snapshot> takeSnapshots() {
    final ComposableFuture<Snapshot> future = new ComposableFuture<>();
    stateContext.execute(
        () -> {
          try {
            final long startTime = System.currentTimeMillis();
            future.complete(snapshot());
            metrics.snapshotTime(System.currentTimeMillis() - startTime);
          } catch (final Exception e) {
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  /**
   * Schedules a completion check for the snapshot at the given index.
   *
   * @param snapshot the snapshot to complete
   */
  private void scheduleCompletion(final Snapshot snapshot) {
    stateContext.schedule(getSnapshotCompletionDelay(), () -> tryToCompleteSnapshot(snapshot));
  }

  private void tryToCompleteSnapshot(final Snapshot snapshot) {
    if (completeSnapshot(snapshot.index())) {
      logger.debug("Completing snapshot {}", snapshot.index());
      try {
        snapshot.complete();
      } catch (final AtomixIOException e) {
        logger.error("Failed to complete snapshot {}, rescheduling completion", snapshot, e);
        scheduleCompletion(snapshot);
        return;
      } catch (final Exception e) {
        logger.error("Failed to complete snapshot {}, rescheduling snapshots", snapshot, e);
        snapshot.close();
        scheduleSnapshots();
        return;
      }

      // If log compaction is being forced, immediately compact the logs.
      if (!raft.getLoadMonitor().isUnderHighLoad()
          || isRunningOutOfDiskSpace()
          || isRunningOutOfMemory()) {
        compactLogs(snapshot.index());
      } else {
        scheduleCompaction(snapshot.index());
      }
    } else {
      scheduleCompletion(snapshot);
    }
  }

  /**
   * Schedules a log compaction.
   *
   * @param lastApplied the last applied index at the start of snapshotting. This represents the
   *     highest index before which segments can be safely removed from disk
   */
  private void scheduleCompaction(final long lastApplied) {
    final Duration compactDelay = getCompactDelay();
    // Schedule compaction after a randomized delay to discourage snapshots on multiple nodes at the
    // same time.
    logger.trace("Scheduling compaction in {}", compactDelay);
    stateContext.schedule(compactDelay, () -> compactLogs(lastApplied));
  }

  /**
   * Compacts logs up to the given index.
   *
   * @param compactIndex the index to which to compact logs
   */
  private void compactLogs(final long compactIndex) {
    raft.getThreadContext()
        .execute(
            () -> {
              logger.debug("Compacting logs up to index {}", compactIndex);
              try {
                final long startTime = System.currentTimeMillis();
                raft.getLog().compact(compactIndex);
                metrics.compactionTime(System.currentTimeMillis() - startTime);
              } catch (final Exception e) {
                logger.error("An exception occurred during log compaction: {}", e);
              } finally {
                this.compactFuture.complete(null);
                this.compactFuture = null;
                // Immediately attempt to take new snapshots since compaction is already run after a
                // time interval.
                takeSnapshots(false, false);
              }
            });
  }

  /** Takes snapshots for the given index. */
  Snapshot snapshot() {
    final Snapshot snapshot =
        raft.getSnapshotStore()
            .newSnapshot(getCompactableIndex(), getCompactableTerm(), new WallClockTimestamp());
    try (final SnapshotWriter writer = snapshot.openWriter()) {
      for (final RaftServiceContext service : raft.getServices()) {
        writer.buffer().mark();
        final SnapshotWriter serviceWriter =
            new SnapshotWriter(writer.buffer().writeInt(0).slice(), writer.snapshot());
        snapshotService(serviceWriter, service);
        final int length = serviceWriter.buffer().position();
        writer.buffer().reset().writeInt(length).skip(length);
      }
    } catch (final Exception e) {
      snapshot.close();
      logger.error("Failed to snapshot services", e);
      throw e;
    }
    return snapshot;
  }

  /**
   * Takes a snapshot of the given service.
   *
   * @param writer the snapshot writer
   * @param service the service to snapshot
   */
  private void snapshotService(final SnapshotWriter writer, final RaftServiceContext service) {
    writer.writeLong(service.serviceId().id());
    writer.writeString(service.serviceType().name());
    writer.writeString(service.serviceName());
    final byte[] config =
        Serializer.using(service.serviceType().namespace()).encode(service.serviceConfig());
    writer.writeInt(config.length).writeBytes(config);
    try {
      service.takeSnapshot(writer);
    } catch (final Exception e) {
      logger.error("Failed to take snapshot of service {}", service.serviceId(), e);
    }
  }

  /**
   * Determines whether to complete the snapshot at the given index.
   *
   * @param index the index of the snapshot to complete
   * @return whether to complete the snapshot at the given index
   */
  private boolean completeSnapshot(final long index) {
    // Compute the lowest completed index for all sessions that belong to this state machine.
    long lastCompleted = index;
    for (final RaftSession session : raft.getSessions().getSessions()) {
      lastCompleted = Math.min(lastCompleted, session.getLastCompleted());
    }
    return lastCompleted >= index;
  }

  protected Duration getCompactDelay() {
    return COMPACT_DELAY;
  }

  protected Duration getSnapshotCompletionDelay() {
    return SNAPSHOT_COMPLETION_DELAY;
  }

  protected Duration getSnapshotInterval() {
    return SNAPSHOT_INTERVAL;
  }

  /** Returns a boolean indicating whether the node is running out of disk space. */
  private boolean isRunningOutOfDiskSpace() {
    // If there's not enough space left to allocate two log segments
    return raft.getStorage().statistics().getUsableSpace()
            < raft.getStorage().maxLogSegmentSize() * SEGMENT_BUFFER_FACTOR
        // Or the used disk percentage has surpassed the free disk buffer percentage
        || raft.getStorage().statistics().getUsableSpace()
                / (double) raft.getStorage().statistics().getTotalSpace()
            < raft.getStorage().freeDiskBuffer();
  }

  /** Returns a boolean indicating whether the node is running out of memory. */
  private boolean isRunningOutOfMemory() {
    final long freeMemory = raft.getStorage().statistics().getFreeMemory();
    final long totalMemory = raft.getStorage().statistics().getTotalMemory();
    if (freeMemory > 0 && totalMemory > 0) {
      return freeMemory / (double) totalMemory < raft.getStorage().freeMemoryBuffer();
    }
    return false;
  }
}
