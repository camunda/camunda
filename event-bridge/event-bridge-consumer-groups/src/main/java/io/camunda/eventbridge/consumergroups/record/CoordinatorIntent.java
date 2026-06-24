/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.record;

import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * Intents for the coordinator stream. The {@link io.camunda.zeebe.stream.impl.StreamProcessor}
 * dispatches commands by {@code (ValueType, Intent)} to a processor and events by {@code Intent} to
 * an applier (see {@link io.camunda.eventbridge.stream.RecordProcessingEngine}).
 *
 * <p><b>Intent values are constrained by the borrowed {@link
 * io.camunda.zeebe.protocol.record.ValueType}.</b> A coordinator record rides a placeholder engine
 * value type (see {@link EventBridgeRecordValues}), and the platform resolves a record's intent
 * against <em>that</em> value type's own enum on (de)serialization — keeping only the numeric
 * value. So each intent's value must be a valid constant value of its value type's intent enum:
 *
 * <ul>
 *   <li>offset commit rides {@code CHECKPOINT} → {@code CheckpointIntent} (values 0–8);
 *   <li>membership rides {@code CLOCK} → {@code ClockIntent} (values 0–3, hence the four membership
 *       intents occupy exactly 0–3);
 *   <li>rebalance rides {@code SCALE} → {@code ScaleIntent} (values 1–7).
 * </ul>
 *
 * Event values are additionally kept globally distinct (the event-applier registry is keyed by
 * value); command values only need to be distinct within their value type (that registry is keyed
 * by {@code (ValueType, value)}). Removing the borrowed-value-type hack (see the event-bridge TODO)
 * would lift these constraints.
 */
public enum CoordinatorIntent implements Intent {
  /** Command: a consumer asks the coordinator to commit an offset (CHECKPOINT). */
  COMMIT_OFFSET((short) 0, false),
  /** Event: the offset has been (monotonically) committed to replicated state (CHECKPOINT). */
  OFFSET_COMMITTED((short) 4, true),

  /** Command: a consumer asks to join a group (CLOCK). */
  JOIN_GROUP((short) 0, false),
  /** Event: a member has joined; the group epoch is bumped and the member added (CLOCK). */
  MEMBER_JOINED((short) 1, true),

  /** Command: a member asks to leave (also written on session eviction) (CLOCK). */
  LEAVE_GROUP((short) 2, false),
  /** Event: a member has left; the group epoch is bumped and the member removed (CLOCK). */
  MEMBER_LEFT((short) 3, true),

  /** Command: the async assignor proposes a target assignment for a group epoch (SCALE). */
  REBALANCE_GROUP((short) 6, false),
  /** Event: a target assignment has been committed; member targets + epoch are set (SCALE). */
  GROUP_REBALANCED((short) 7, true);

  private final short value;
  private final boolean isEvent;

  CoordinatorIntent(final short value, final boolean isEvent) {
    this.value = value;
    this.isEvent = isEvent;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return isEvent;
  }
}
