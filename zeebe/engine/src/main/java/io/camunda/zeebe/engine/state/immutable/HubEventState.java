/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import java.util.Optional;
import java.util.OptionalLong;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * How far partition 1 has handled the Hub events of its physical tenant, whatever their type. Hub
 * sends its events in one sequence per physical tenant, and partition 1 handles them one at a time.
 * An applied or rejected event is finished: every event up to the last finished Hub sequence is
 * handled, and is never handled again. The event after it may still be in progress. The other
 * partitions record nothing.
 */
@NullMarked
public interface HubEventState {

  /** The Hub event with the highest sequence, finished or not, if partition 1 received any. */
  Optional<HubEvent> getLast();

  /** The last applied or rejected Hub event, if any is finished. */
  Optional<HubEvent> getLastFinished();

  /** The Hub sequence of the last applied Hub event, if any was applied. */
  OptionalLong getLastAppliedSeqNo();

  /**
   * A Hub event that partition 1 received.
   *
   * @param seqNo the Hub sequence of the event
   * @param type what kind of event it is
   * @param status how far the event is handled
   * @param reason why the event was rejected, ran into an error or was canceled; {@code null}
   *     otherwise
   */
  record HubEvent(long seqNo, HubEventType type, HubEventStatus status, @Nullable String reason) {}

  /**
   * What kind of Hub event it is. Hub sends events of every type in one sequence, so the type tells
   * which state holds the rest of an event in progress.
   */
  enum HubEventType {
    /** A policy change set, handled as a policy update. */
    POLICY_UPDATE
  }

  /** How far a Hub event is handled. */
  enum HubEventStatus {
    /** Applied, and reported to Hub as applied; the event is finished. */
    APPLIED,
    /** Not applied for a reason, which a retry would not change; the event is finished. */
    REJECTED,
    /** Received; its chunks are being collected, and nothing of it is applied yet. */
    BUFFERING,
    /** Received; it is being applied. */
    APPLYING,
    /** Received, but something transient went wrong; the event may be tried again. */
    ERROR,
    /**
     * Canceled while buffering, for example as it hung after a leader change; nothing of it is
     * applied, so the event may be tried again.
     */
    CANCELED;

    /** Whether the event is applied or rejected, so the Hub sequence moves past it. */
    public boolean isFinished() {
      return this == APPLIED || this == REJECTED;
    }
  }
}
