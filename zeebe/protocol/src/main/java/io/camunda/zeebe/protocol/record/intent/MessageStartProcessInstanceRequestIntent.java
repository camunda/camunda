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
 * Intents of the cross-partition handshake that lets {@code P_K} (the message-correlation partition
 * for a given {@code correlationKey}) delegate the creation of a message-start process instance to
 * {@code P_B = hash(businessId)}, the partition that owns the businessId uniqueness invariant.
 *
 * <p>The handshake is split into a request half ({@link #REQUEST} / {@link #REQUESTED}) carried
 * from {@code P_K} to {@code P_B}, and one of three reply halves carried back from {@code P_B} to
 * {@code P_K}: success ({@link #START} / {@link #STARTED}), uniqueness conflict ({@link
 * #REJECT_UNIQUENESS} / {@link #UNIQUENESS_REJECTED}), or missing start-event subscription on
 * {@code P_B} ({@link #REJECT_NO_SUBSCRIPTION} / {@link #NO_SUBSCRIPTION_REJECTED}).
 *
 * <p>The {@code *_REJECTED} reply intents do not carry the engine's banning semantics: a remote
 * rejection from {@code P_B} reflects live state on the destination partition (uniqueness lock held
 * or deployment not yet distributed), not a programming error on {@code P_K}, and so this intent
 * intentionally does not implement {@link ProcessInstanceRelatedIntent}.
 *
 * <p>{@link #SWEEP_TOMBSTONES} and {@link #TOMBSTONE_DELETED} are internal to {@code P_B}: a
 * scheduled task on {@code P_B} writes a {@code SWEEP_TOMBSTONES} trigger; the matching batch
 * processor walks the dedup state and emits one {@code TOMBSTONE_DELETED} event per past-deadline
 * tombstone entry, whose applier removes the entry from both dedup column families.
 */
public enum MessageStartProcessInstanceRequestIntent implements Intent {

  // request half, applied on P_B
  REQUEST((short) 0, false),
  REQUESTED((short) 1, true),

  // success reply half. STARTED is applied on P_B (dedup write) and, once the P_K-side bookkeeping
  // lands in a later commit, on P_K (pending-ask cleanup + commit of the started PI).
  START((short) 2, false),
  STARTED((short) 3, true),

  // uniqueness-rejected reply half, applied on P_K only
  REJECT_UNIQUENESS((short) 4, false),
  UNIQUENESS_REJECTED((short) 5, true),

  // no-subscription-rejected reply half, applied on P_K only
  REJECT_NO_SUBSCRIPTION((short) 6, false),
  NO_SUBSCRIPTION_REJECTED((short) 7, true),

  // tombstone-sweep on P_B: SWEEP_TOMBSTONES is the scheduler trigger, TOMBSTONE_DELETED is the
  // per-entry deletion event whose applier removes the dedup entry from both column families.
  SWEEP_TOMBSTONES((short) 8, false),
  TOMBSTONE_DELETED((short) 9, true);

  private final short value;
  private final boolean isEvent;

  MessageStartProcessInstanceRequestIntent(final short value, final boolean isEvent) {
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

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return REQUEST;
      case 1:
        return REQUESTED;
      case 2:
        return START;
      case 3:
        return STARTED;
      case 4:
        return REJECT_UNIQUENESS;
      case 5:
        return UNIQUENESS_REJECTED;
      case 6:
        return REJECT_NO_SUBSCRIPTION;
      case 7:
        return NO_SUBSCRIPTION_REJECTED;
      case 8:
        return SWEEP_TOMBSTONES;
      case 9:
        return TOMBSTONE_DELETED;
      default:
        return Intent.UNKNOWN;
    }
  }
}
