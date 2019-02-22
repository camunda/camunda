/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.raft.controller;

import static io.zeebe.raft.AppendRequestEncoder.previousEventPositionNullValue;
import static io.zeebe.raft.AppendRequestEncoder.previousEventTermNullValue;

import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.raft.Loggers;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftMember;
import io.zeebe.raft.backpressure.BackpressureHelper;
import io.zeebe.raft.protocol.AppendRequest;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorPriority;
import io.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;
import org.slf4j.Logger;

/** Per-follower replication controller */
public class MemberReplicateLogController extends Actor implements Service<Void> {
  /** TODO: remove constant, follower should tell us on join or other request */
  public static final int REMOTE_BUFFER_SIZE = 1024 * 1024 * 2;

  private static final Logger LOG = Loggers.RAFT_LOGGER;
  private static final boolean IS_TRACE_ENABLED = LOG.isTraceEnabled();

  private final AppendRequest appendRequest = new AppendRequest();

  private final BackpressureHelper backpressureHelper = new BackpressureHelper(REMOTE_BUFFER_SIZE);

  private long lastRequestTimestamp;

  final Runnable sendNextEventsFn = this::sendNextEvents;

  private final Raft raft;
  private final LogStream logStream;
  private final Duration heartbeatInterval;
  private final int nodeId;
  private final ClientOutput clientOutput;

  private final BufferedLogStreamReader reader;
  private LoggedEventImpl bufferedEvent;
  private long previousPosition;
  private int previousTerm;

  private ActorCondition appenderCondition;
  private final String name;

  private RaftMember member;

  private volatile boolean isClosing = false;

  public MemberReplicateLogController(
      Raft raft, RaftMember member, ClientTransport clientTransport) {
    this.member = member;
    this.nodeId = member.getNodeId();
    this.name = String.format("raft-repl-%s-%d", raft.getName(), nodeId);

    this.raft = raft;
    this.heartbeatInterval = raft.getConfiguration().getHeartbeatIntervalDuration();
    this.clientOutput = clientTransport.getOutput();
    this.logStream = raft.getLogStream();
    this.reader = new BufferedLogStreamReader(logStream);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    startContext.async(startContext.getScheduler().submitActor(this, true));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    // signal closing to abort try send loop
    isClosing = true;
    stopContext.async(actor.close());
  }

  @Override
  protected void onActorStarted() {
    member.setReplicationController(this);

    if (IS_TRACE_ENABLED) {
      LOG.trace("started");
    }

    actor.runAtFixedRate(heartbeatInterval, this::onHeartbeatTimerFired);
    appenderCondition = actor.onCondition("data-appended", this::onAppendPositionChanged);
    raft.getLogStream().registerOnAppendCondition(appenderCondition);

    reset();
  }

  @Override
  protected void onActorClosing() {
    member.setReplicationController(null);

    raft.getLogStream().removeOnCommitPositionUpdatedCondition(appenderCondition);
  }

  @Override
  protected void onActorClosed() {
    if (IS_TRACE_ENABLED) {
      LOG.trace("closed");
    }

    reader.close();
  }

  private void onHeartbeatTimerFired() {
    if (IS_TRACE_ENABLED) {
      LOG.trace("heartbeat timer fired");
    }

    actor.runUntilDone(sendNextEventsFn);
  }

  private void onAppendPositionChanged() {
    if (IS_TRACE_ENABLED) {
      LOG.trace("events appended");
    }

    actor.runUntilDone(sendNextEventsFn);
  }

  public void onFollowerHasAcknowledgedPosition(long position) {
    actor.run(
        () -> {
          if (IS_TRACE_ENABLED) {
            LOG.trace("follower acknowledged position {}", position);
          }
          backpressureHelper.onEventAcknowledged(position);
          actor.runUntilDone(sendNextEventsFn);
        });
  }

  public void onFollowerHasFailedPosition(long position) {
    actor.run(
        () -> {
          if (IS_TRACE_ENABLED) {
            LOG.trace("follower failed position {}", position);
          }
          backpressureHelper.reset();
          resetToPosition(position);
          actor.runUntilDone(sendNextEventsFn);
        });
  }

  private void sendNextEvents() {
    if (IS_TRACE_ENABLED) {
      LOG.trace("try send next event to node {}", nodeId);
    }

    actor.setPriority(ActorPriority.REGULAR);

    final LoggedEventImpl nextEvent = getNextEvent();

    appendRequest
        .reset()
        .setRaft(raft)
        .setPreviousEventPosition(previousPosition)
        .setPreviousEventTerm(previousTerm)
        .setEvent(nextEvent);

    final int requestSize = appendRequest.getLength();
    final long now = ActorClock.currentTimeMillis();
    final boolean isHeartbeatTimeout = now - lastRequestTimestamp >= heartbeatInterval.toMillis();
    final boolean isBackpressured = !backpressureHelper.canSend(requestSize);
    final boolean trySend = isHeartbeatTimeout || (nextEvent != null && !isBackpressured);

    if (trySend && !isClosing) {
      if (clientOutput.sendMessage(nodeId, appendRequest)) {
        lastRequestTimestamp = now;

        if (nextEvent != null) {
          backpressureHelper.onEventSent(nextEvent.getPosition(), requestSize);
          setPreviousEvent(nextEvent);
        }
      } else {
        setBufferedEvent(nextEvent);

        if (isHeartbeatTimeout) {
          actor.setPriority(ActorPriority.HIGH);
        } else {
          actor.setPriority(ActorPriority.LOW);
        }

        actor.yield();
      }
    } else {
      actor.done();
    }
  }

  private void setBufferedEvent(final LoggedEventImpl bufferedEvent) {
    this.bufferedEvent = bufferedEvent;
  }

  private LoggedEventImpl discardBufferedEvent() {
    final LoggedEventImpl event = bufferedEvent;
    bufferedEvent = null;
    return event;
  }

  private void reset() {
    setPreviousEventToEndOfLog();
  }

  private LoggedEventImpl getNextEvent() {
    if (bufferedEvent != null) {
      return discardBufferedEvent();
    } else if (reader.hasNext()) {
      return (LoggedEventImpl) reader.next();
    } else {
      return null;
    }
  }

  private void resetToPosition(final long eventPosition) {
    if (eventPosition >= 0) {
      final LoggedEvent previousEvent = getEventAtPosition(eventPosition);
      if (previousEvent != null) {
        setPreviousEvent(previousEvent);
      } else {
        final LogBlockIndex logBlockIndex = logStream.getLogBlockIndex();
        final long blockPosition = logBlockIndex.lookupBlockPosition(eventPosition);

        if (blockPosition > 0) {
          reader.seek(blockPosition);
        } else {
          reader.seekToFirstEvent();
        }

        long previousPosition = -1;

        while (reader.hasNext()) {
          final LoggedEvent next = reader.next();

          if (next.getPosition() < eventPosition) {
            previousPosition = next.getPosition();
          } else {
            break;
          }
        }

        if (previousPosition >= 0) {
          setPreviousEvent(previousPosition);
        } else {
          setPreviousEventToStartOfLog();
        }
      }
    } else {
      setPreviousEventToStartOfLog();
    }
  }

  private LoggedEvent getEventAtPosition(final long position) {
    if (reader.seek(position) && reader.hasNext()) {
      return reader.next();
    } else {
      return null;
    }
  }

  private void setPreviousEventToEndOfLog() {
    discardBufferedEvent();

    reader.seekToLastEvent();

    final LoggedEventImpl lastEvent = getNextEvent();
    setPreviousEvent(lastEvent);
  }

  private void setPreviousEventToStartOfLog() {
    discardBufferedEvent();

    reader.seekToFirstEvent();

    setPreviousEvent(null);
  }

  private void setPreviousEvent(final long previousPosition) {
    discardBufferedEvent();

    final LoggedEvent previousEvent = getEventAtPosition(previousPosition);

    setPreviousEvent(previousEvent);
  }

  private void setPreviousEvent(final LoggedEvent previousEvent) {
    discardBufferedEvent();

    if (previousEvent != null) {
      previousPosition = previousEvent.getPosition();
      previousTerm = previousEvent.getRaftTerm();
    } else {
      previousPosition = previousEventPositionNullValue();
      previousTerm = previousEventTermNullValue();
    }
  }

  @Override
  public Void get() {
    return null;
  }
}
