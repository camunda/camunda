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
 * Intents of the pull-based release lookup that lets {@code P_K} (the partition that owns a
 * correlation-key lock for a message-start instance created via the cross-partition handshake)
 * discover when its remote holder instance has completed on {@code P_B = hash(businessId)}, so the
 * lock can be released and the next buffered message for that correlation key picked up.
 *
 * <p>For a message-start instance created locally the correlation-key lock is released when the
 * holder completes on the same partition. For one created via the cross-partition ask the holder
 * lives on {@code P_B}, which {@code P_K} cannot observe directly. Rather than {@code P_B} pushing
 * a completion notification, {@code P_K} polls: it asks {@code P_B} whether a specific holder
 * instance is still active, and acts on the reply. The lookup is reconstructable from {@code P_K}'s
 * local lock state and is self-healing — a dropped poll simply retries on the next tick.
 *
 * <p>The flow is split into a query half ({@link #QUERY} / {@link #QUERIED}) carried from {@code
 * P_K} to {@code P_B}, and a release reply ({@link #RELEASE} / {@link #RELEASED}) carried back from
 * {@code P_B} to {@code P_K} only when the holder is gone. While the holder is still active {@code
 * P_B} stays silent — {@code P_K} keeps polling with back-off until it observes completion — so
 * there is no "still active" reply intent.
 *
 * <p>{@link #PUSHED} is the push counterpart, applied on {@code P_B} when a cross-partition holder
 * completes or terminates: {@code P_B} pushes a {@link #RELEASE} straight to {@code P_K} instead of
 * waiting to be polled, and drops the holder-origin bookkeeping it kept for that purpose. The query
 * half above then only has to cover the rare cases the push cannot (e.g. a holder lost before it
 * pushed), acting as a self-healing reconciliation fallback rather than the primary path.
 *
 * <p>The target partition of a {@link #QUERY} is derived from the holder instance key (every Zeebe
 * key encodes its generating partition); the target partition of the {@link #RELEASE} reply is
 * derived from the {@code requestKey} — stamped by {@code P_K} on the poll path, synthesized by
 * {@code P_B} from the buffered message key's partition bits on the push path.
 */
public enum MessageStartCorrelationKeyLockReleaseIntent implements Intent {

  // query half, applied on P_B
  QUERY((short) 0, false),
  QUERIED((short) 1, true),

  // release reply half, applied on P_K only when the holder instance is no longer active on P_B
  RELEASE((short) 2, false),
  RELEASED((short) 3, true),

  // push half, applied on P_B when a cross-partition holder completes/terminates: it pushes a
  // RELEASE to P_K without waiting to be polled and drops the holder-origin bookkeeping
  PUSHED((short) 4, true);

  private final short value;
  private final boolean isEvent;

  MessageStartCorrelationKeyLockReleaseIntent(final short value, final boolean isEvent) {
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
        return QUERY;
      case 1:
        return QUERIED;
      case 2:
        return RELEASE;
      case 3:
        return RELEASED;
      case 4:
        return PUSHED;
      default:
        return Intent.UNKNOWN;
    }
  }
}
