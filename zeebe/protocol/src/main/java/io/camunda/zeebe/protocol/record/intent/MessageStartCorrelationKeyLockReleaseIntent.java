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
 * Intents of the cross-partition correlation-key lock release: how {@code P_K} (the partition that
 * owns a correlation-key lock for a message-start instance created via the cross-partition
 * handshake) learns that its remote holder instance has completed on {@code P_B =
 * hash(businessId)}, so the lock can be released and the next buffered message for that correlation
 * key picked up.
 *
 * <p>For a message-start instance created locally the correlation-key lock is released when the
 * holder completes on the same partition. For one created via the cross-partition ask the holder
 * lives on {@code P_B}, which {@code P_K} cannot observe directly. The primary mechanism is a push:
 * when the holder completes or terminates, {@code P_B} sends a {@link #RELEASE} straight to {@code
 * P_K} (see {@link #PUSHED}), so the lock is released at completion latency without {@code P_K}
 * having to poll.
 *
 * <p>{@link #PUSHED} is applied on {@code P_B} when a cross-partition holder completes or
 * terminates: {@code P_B} pushes a {@link #RELEASE} to {@code P_K} and drops the holder-origin
 * bookkeeping the {@code STARTED} applier kept for that purpose. The {@link #RELEASE} / {@link
 * #RELEASED} reply half is applied on {@code P_K}: it releases the lock and picks up the next
 * buffered message. The reply is idempotent — a redundant {@code RELEASE} for an already-released
 * lock is rejected — so a push and a reconciling poll racing on the same holder cannot
 * double-release.
 *
 * <p>The query half ({@link #QUERY} / {@link #QUERIED}) is the reconciliation backstop, not the
 * primary path: a coarse {@code P_K} poll asks {@code P_B} whether specific holders are still
 * active and reconciles any whose push was lost (or that were banned, so had no transition to push
 * from). It is reconstructable from {@code P_K}'s local lock state and self-healing — a lock still
 * held is simply re-queried on a later tick. {@code P_B} replies {@link #RELEASE} for a holder that
 * is gone and stays silent for one still active, so there is no "still active" reply intent.
 *
 * <p>The target partition of a {@link #QUERY} is derived from the holder instance key (every Zeebe
 * key encodes its generating partition); the target partition of the {@link #RELEASE} reply is
 * derived from the {@code requestKey} — synthesized by {@code P_B} from the buffered message key's
 * partition bits on the push path, stamped by {@code P_K} on the reconciliation path.
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
