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
package io.camunda.zeebe.protocol.record.intent;

/**
 * Intents for the event-bridge consumer-group coordinator stream. They ride three value types —
 * {@link io.camunda.zeebe.protocol.record.ValueType#EVENT_BRIDGE_MEMBERSHIP}, {@code
 * EVENT_BRIDGE_OFFSET}, {@code EVENT_BRIDGE_REBALANCE} — all of which map to this single enum (as,
 * e.g., the batch-operation value types share {@code BatchOperationIntent}). Values are therefore
 * globally unique across the three so a record's intent resolves unambiguously from its numeric
 * value alone.
 */
public enum CoordinatorIntent implements Intent {
  /** Command: a consumer asks to join a group (EVENT_BRIDGE_MEMBERSHIP). */
  JOIN_GROUP((short) 0, false),
  /** Event: a member has joined; the group epoch is bumped and the member added. */
  MEMBER_JOINED((short) 1, true),
  /** Command: a member asks to leave (also written on session eviction). */
  LEAVE_GROUP((short) 2, false),
  /** Event: a member has left; the group epoch is bumped and the member removed. */
  MEMBER_LEFT((short) 3, true),

  /** Command: a consumer asks the coordinator to commit an offset (EVENT_BRIDGE_OFFSET). */
  COMMIT_OFFSET((short) 4, false),
  /** Event: the offset has been (monotonically) committed to replicated state. */
  OFFSET_COMMITTED((short) 5, true),

  /** Command: the async assignor proposes a target assignment (EVENT_BRIDGE_REBALANCE). */
  REBALANCE_GROUP((short) 6, false),
  /** Event: a target assignment has been committed; member targets + epoch are set. */
  GROUP_REBALANCED((short) 7, true),

  /**
   * Command: a member reports it has reconciled to the current target (EVENT_BRIDGE_MEMBERSHIP).
   */
  RECONCILE_MEMBER((short) 8, false),
  /** Event: a member's assigned epoch advanced to the group epoch (converged to the target). */
  MEMBER_RECONCILED((short) 9, true),
  /** Command: reclaim an empty group after its retention elapses (EVENT_BRIDGE_MEMBERSHIP). */
  DELETE_GROUP((short) 10, false),
  /** Event: an empty group (and its offsets) has been removed. */
  GROUP_DELETED((short) 11, true);

  private final short value;
  private final boolean isEvent;

  CoordinatorIntent(final short value, final boolean isEvent) {
    this.value = value;
    this.isEvent = isEvent;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return JOIN_GROUP;
      case 1:
        return MEMBER_JOINED;
      case 2:
        return LEAVE_GROUP;
      case 3:
        return MEMBER_LEFT;
      case 4:
        return COMMIT_OFFSET;
      case 5:
        return OFFSET_COMMITTED;
      case 6:
        return REBALANCE_GROUP;
      case 7:
        return GROUP_REBALANCED;
      case 8:
        return RECONCILE_MEMBER;
      case 9:
        return MEMBER_RECONCILED;
      case 10:
        return DELETE_GROUP;
      case 11:
        return GROUP_DELETED;
      default:
        return Intent.UNKNOWN;
    }
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
