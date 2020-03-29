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
package io.atomix.raft.session;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Lists;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.operation.OperationType;
import io.atomix.primitive.session.Session;
import io.atomix.primitive.session.SessionId;
import io.atomix.primitive.session.impl.AbstractSession;
import io.atomix.raft.ReadConsistency;
import io.atomix.raft.impl.OperationResult;
import io.atomix.raft.impl.PendingCommand;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.PublishRequest;
import io.atomix.raft.protocol.RaftServerProtocol;
import io.atomix.raft.service.RaftServiceContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.concurrent.ThreadContextFactory;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import io.atomix.utils.misc.TimestampPrinter;
import io.atomix.utils.serializer.Serializer;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.LongAccumulator;
import org.slf4j.Logger;

/** Raft session. */
public class RaftSession extends AbstractSession {

  private final Logger log;
  private final ReadConsistency readConsistency;
  private final long minTimeout;
  private final long maxTimeout;
  private final RaftServerProtocol protocol;
  private final RaftServiceContext context;
  private final RaftContext server;
  private final ThreadContext eventExecutor;
  private final Map<Long, List<Runnable>> sequenceQueries = new HashMap<>();
  private final Map<Long, List<Runnable>> indexQueries = new HashMap<>();
  private final Map<Long, PendingCommand> pendingCommands = new HashMap<>();
  private final Map<Long, OperationResult> results = new HashMap<>();
  private final Queue<EventHolder> events = new LinkedList<>();
  private volatile State state = State.CLOSED;
  private volatile long lastUpdated;
  private final LongAccumulator requestSequence;
  private volatile long commandSequence;
  private volatile long lastApplied;
  private volatile long commandLowWaterMark;
  private volatile long eventIndex;
  private volatile long completeIndex;
  private volatile EventHolder currentEventList;

  public RaftSession(
      final SessionId sessionId,
      final MemberId member,
      final String name,
      final PrimitiveType primitiveType,
      final ReadConsistency readConsistency,
      final long minTimeout,
      final long maxTimeout,
      final long lastUpdated,
      final Serializer serializer,
      final RaftServiceContext context,
      final RaftContext server,
      final ThreadContextFactory threadContextFactory) {
    super(sessionId, name, primitiveType, member, serializer);
    this.readConsistency = readConsistency;
    this.minTimeout = minTimeout;
    this.maxTimeout = maxTimeout;
    this.lastUpdated = lastUpdated;
    this.eventIndex = sessionId.id();
    this.completeIndex = sessionId.id();
    this.lastApplied = sessionId.id();
    this.protocol = server.getProtocol();
    this.context = context;
    this.server = server;
    this.eventExecutor = threadContextFactory.createContext();
    this.log =
        ContextualLoggerFactory.getLogger(
            getClass(),
            LoggerContext.builder(Session.class)
                .addValue(sessionId)
                .add("type", context.serviceType())
                .add("name", context.serviceName())
                .build());
    this.requestSequence = new LongAccumulator(Long::max, 0);
  }

  /**
   * Returns the session read consistency.
   *
   * @return the session read consistency
   */
  public ReadConsistency readConsistency() {
    return readConsistency;
  }

  /**
   * Returns the minimum session timeout.
   *
   * @return the minimum session timeout
   */
  public long minTimeout() {
    return minTimeout;
  }

  /**
   * Returns the maximum session timeout.
   *
   * @return the maximum session timeout
   */
  public long maxTimeout() {
    return maxTimeout;
  }

  /**
   * Returns a boolean indicating whether the session is timed out.
   *
   * @param timestamp the current timestamp
   * @return indicates whether the session is timed out
   */
  public boolean isTimedOut(final long timestamp) {
    final long lastUpdated = this.lastUpdated;
    return lastUpdated > 0 && timestamp - lastUpdated > maxTimeout;
  }

  /**
   * Returns the next request sequence number.
   *
   * @return the next request sequence number
   */
  public long nextRequestSequence() {
    return this.requestSequence.get() + 1;
  }

  /**
   * Resets the current request sequence number.
   *
   * @param requestSequence The request sequence number.
   */
  public void resetRequestSequence(final long requestSequence) {
    // If the request sequence number is less than the applied sequence number, update the request
    // sequence number. This is necessary to ensure that if the local server is a follower that is
    // later elected leader, its sequences are consistent for commands.
    // TODO: what's the difference with this and `setRequestSequence`? logic was also previously the
    // same
    this.requestSequence.accumulate(requestSequence);
  }

  /**
   * Returns the next operation sequence number.
   *
   * @return The next operation sequence number.
   */
  public long nextCommandSequence() {
    return commandSequence + 1;
  }

  /**
   * Registers a causal session query.
   *
   * @param sequence The session sequence number at which to execute the query.
   * @param query The query to execute.
   */
  public void registerSequenceQuery(final long sequence, final Runnable query) {
    // Add a query to be run once the session's sequence number reaches the given sequence number.
    final List<Runnable> queries =
        this.sequenceQueries.computeIfAbsent(sequence, v -> new LinkedList<Runnable>());
    queries.add(query);
  }

  /**
   * Registers a session index query.
   *
   * @param index The state machine index at which to execute the query.
   * @param query The query to execute.
   */
  public void registerIndexQuery(final long index, final Runnable query) {
    // Add a query to be run once the session's index reaches the given index.
    final List<Runnable> queries =
        this.indexQueries.computeIfAbsent(index, v -> new LinkedList<>());
    queries.add(query);
  }

  /**
   * Registers a pending command.
   *
   * @param sequence the pending command sequence number
   * @param pendingCommand the pending command to register
   */
  public void registerCommand(final long sequence, final PendingCommand pendingCommand) {
    pendingCommands.put(sequence, pendingCommand);
  }

  /**
   * Gets a pending command.
   *
   * @param sequence the pending command sequence number
   * @return the pending command or {@code null} if no command is pending for this sequence number
   */
  public PendingCommand getCommand(final long sequence) {
    return pendingCommands.get(sequence);
  }

  /**
   * Removes and returns a pending command.
   *
   * @param sequence the pending command sequence number
   * @return the pending command or {@code null} if no command is pending for this sequence number
   */
  public PendingCommand removeCommand(final long sequence) {
    return pendingCommands.remove(sequence);
  }

  /**
   * Clears and returns all pending commands.
   *
   * @return a collection of pending commands
   */
  public Collection<PendingCommand> clearCommands() {
    final Collection<PendingCommand> commands = Lists.newArrayList(pendingCommands.values());
    pendingCommands.clear();
    return commands;
  }

  /**
   * Registers a session result.
   *
   * <p>Results are stored in memory on all servers in order to provide linearizable semantics. When
   * a command is applied to the state machine, the command's return value is stored with the
   * sequence number. Once the client acknowledges receipt of the command output the result will be
   * cleared from memory.
   *
   * @param sequence The result sequence number.
   * @param result The result.
   */
  public void registerResult(final long sequence, final OperationResult result) {
    setRequestSequence(sequence);
    results.put(sequence, result);
  }

  /**
   * Clears command results up to the given sequence number.
   *
   * <p>Command output is removed from memory up to the given sequence number. Additionally, since
   * we know the client received a response for all commands up to the given sequence number,
   * command futures are removed from memory as well.
   *
   * @param sequence The sequence to clear.
   */
  public void clearResults(final long sequence) {
    if (sequence > commandLowWaterMark) {
      for (long i = commandLowWaterMark + 1; i <= sequence; i++) {
        results.remove(i);
        commandLowWaterMark = i;
      }
    }
  }

  /**
   * Returns the session response for the given sequence number.
   *
   * @param sequence The response sequence.
   * @return The response.
   */
  public OperationResult getResult(final long sequence) {
    return results.get(sequence);
  }

  @Override
  public void publish(final PrimitiveEvent event) {
    // Store volatile state in a local variable.
    final State state = this.state;

    // If the sessions's state is not active, just ignore the event.
    if (!state.active()) {
      return;
    }

    // If the event is being published during a read operation, throw an exception.
    checkState(
        context.currentOperation() == OperationType.COMMAND,
        "session events can only be published during command execution");

    // If the client acked an index greater than the current event sequence number since we know the
    // client must have received it from another server.
    if (completeIndex > context.currentIndex()) {
      return;
    }

    // If no event has been published for this index yet, create a new event holder.
    if (this.currentEventList == null
        || this.currentEventList.eventIndex != context.currentIndex()) {
      final long previousIndex = eventIndex;
      eventIndex = context.currentIndex();
      this.currentEventList = new EventHolder(eventIndex, previousIndex);
    }

    // Add the event to the event holder.
    this.currentEventList.events.add(event);
  }

  /** Commits events for the given index. */
  public void commit(final long index) {
    if (currentEventList != null && currentEventList.eventIndex == index) {
      events.add(currentEventList);
      sendEvents(currentEventList);
      currentEventList = null;
    }
    setLastApplied(index);
  }

  /** Sends an event to the session. */
  private void sendEvents(final EventHolder event) {
    // Only send events to the client if this server is the leader.
    if (server.isLeader()) {
      eventExecutor.execute(
          () -> {
            final PublishRequest request =
                PublishRequest.builder()
                    .withSession(sessionId().id())
                    .withEventIndex(event.eventIndex)
                    .withPreviousIndex(event.previousIndex)
                    .withEvents(event.events)
                    .build();

            log.trace("Sending {}", request);
            protocol.publish(memberId(), request);
          });
    }
  }

  /** Opens the session. */
  public void open() {
    setState(State.OPEN);
    protocol.registerResetListener(
        sessionId(),
        request -> resendEvents(request.index()),
        server.getServiceManager().executor());
  }

  /**
   * Resends events from the given sequence.
   *
   * @param index The index from which to resend events.
   */
  public void resendEvents(final long index) {
    clearEvents(index);
    for (final EventHolder event : events) {
      sendEvents(event);
    }
  }

  /**
   * Clears events up to the given sequence.
   *
   * @param index The index to clear.
   */
  private void clearEvents(final long index) {
    if (index > completeIndex) {
      EventHolder event = events.peek();
      while (event != null && event.eventIndex <= index) {
        events.remove();
        completeIndex = event.eventIndex;
        event = events.peek();
      }
      completeIndex = index;
    }
  }

  /** Expires the session. */
  public void expire() {
    setState(State.EXPIRED);
    protocol.unregisterResetListener(sessionId());
  }

  /** Closes the session. */
  public void close() {
    setState(State.CLOSED);
    protocol.unregisterResetListener(sessionId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), sessionId());
  }

  @Override
  public boolean equals(final Object object) {
    return object instanceof Session && ((Session) object).sessionId() == sessionId();
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .addValue(context)
        .add("session", sessionId())
        .add("timestamp", TimestampPrinter.of(lastUpdated))
        .toString();
  }

  /**
   * Returns the session operation sequence number.
   *
   * @return The session operation sequence number.
   */
  public long getCommandSequence() {
    return commandSequence;
  }

  /**
   * Sets the session operation sequence number.
   *
   * @param sequence The session operation sequence number.
   */
  public void setCommandSequence(final long sequence) {
    // For each increment of the sequence number, trigger query callbacks that are dependent on the
    // specific sequence.
    for (long i = commandSequence + 1; i <= sequence; i++) {
      commandSequence = i;
      final List<Runnable> queries = this.sequenceQueries.remove(commandSequence);
      if (queries != null) {
        for (final Runnable query : queries) {
          query.run();
        }
      }
    }
  }

  /**
   * Returns the collection of pending commands.
   *
   * @return the collection of pending commands
   */
  public Collection<PendingCommand> getCommands() {
    return pendingCommands.values();
  }

  /**
   * Returns the session event index.
   *
   * @return The session event index.
   */
  public long getEventIndex() {
    return eventIndex;
  }

  /**
   * Sets the session event index.
   *
   * @param eventIndex the session event index
   */
  public void setEventIndex(final long eventIndex) {
    this.eventIndex = eventIndex;
  }

  /**
   * Returns the session index.
   *
   * @return The session index.
   */
  public long getLastApplied() {
    return lastApplied;
  }

  /**
   * Sets the session index.
   *
   * @param index The session index.
   */
  public void setLastApplied(final long index) {
    // Query callbacks for this session are added to the indexQueries map to be executed once the
    // required index
    // for the query is reached. For each increment of the index, trigger query callbacks that are
    // dependent
    // on the specific index.
    for (long i = lastApplied + 1; i <= index; i++) {
      lastApplied = i;
      final List<Runnable> queries = this.indexQueries.remove(lastApplied);
      if (queries != null) {
        for (final Runnable query : queries) {
          query.run();
        }
      }
    }
  }

  /**
   * Returns the index of the highest event acked for the session.
   *
   * @return The index of the highest event acked for the session.
   */
  public long getLastCompleted() {
    // If there are any queued events, return the index prior to the first event in the queue.
    final EventHolder event = events.peek();
    if (event != null && event.eventIndex > completeIndex) {
      return event.eventIndex - 1;
    }
    // If no events are queued, return the highest index applied to the session.
    return lastApplied;
  }

  /**
   * Sets the last completed event index for the session.
   *
   * @param lastCompleted the last completed index
   */
  public void setLastCompleted(final long lastCompleted) {
    this.completeIndex = lastCompleted;
  }

  /**
   * Returns the session update timestamp.
   *
   * @return The session update timestamp.
   */
  public long getLastUpdated() {
    return lastUpdated;
  }

  /**
   * Updates the session timestamp.
   *
   * @param lastUpdated The session timestamp.
   */
  public void setLastUpdated(final long lastUpdated) {
    this.lastUpdated = Math.max(this.lastUpdated, lastUpdated);
  }

  /**
   * Returns the session request number.
   *
   * @return The session request number.
   */
  public long getRequestSequence() {
    return requestSequence.get();
  }

  /**
   * Sets the current request sequence number.
   *
   * @param requestSequence the current request sequence number
   */
  public void setRequestSequence(final long requestSequence) {
    this.requestSequence.accumulate(requestSequence);
  }

  /**
   * Returns the state machine context associated with the session.
   *
   * @return The state machine context associated with the session.
   */
  public RaftServiceContext getService() {
    return context;
  }

  @Override
  public State getState() {
    return state;
  }

  /**
   * Updates the session state.
   *
   * @param state The session state.
   */
  private void setState(final State state) {
    if (this.state != state) {
      this.state = state;
      log.debug("State changed: {}", state);
    }
  }

  /** Event holder. */
  private static final class EventHolder {

    private final long eventIndex;
    private final long previousIndex;
    private final List<PrimitiveEvent> events = new LinkedList<>();

    private EventHolder(final long eventIndex, final long previousIndex) {
      this.eventIndex = eventIndex;
      this.previousIndex = previousIndex;
    }
  }
}
