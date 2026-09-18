/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import org.agrona.DirectBuffer;

public interface MutableMessageState extends MessageState, StreamProcessorLifecycleAware {

  void put(long messageKey, MessageRecord message);

  void putMessageCorrelation(long messageKey, DirectBuffer bpmnProcessId);

  /**
   * Claims the correlation lock for {@code (messageKey, bpmnProcessId)} on behalf of {@code
   * subscriptionKey}, in addition to the plain {@link #putMessageCorrelation(long, DirectBuffer)}
   * marker. Reclaiming an already-locked message (e.g. rerouting it to a newer-generation
   * subscription after a stale reject released the previous claimant's lock) simply overwrites the
   * recorded owner — a message is correlated to at most one subscription of a given process at a
   * time, so there is never a legitimate second claimant to preserve.
   */
  void putMessageCorrelation(long messageKey, DirectBuffer bpmnProcessId, long subscriptionKey);

  void removeMessageCorrelation(long messageKey, DirectBuffer bpmnProcessId);

  /**
   * Releases the correlation lock for {@code (messageKey, bpmnProcessId)} only if it is not
   * currently owned by a different subscription key than {@code subscriptionKey} (including when it
   * has no recorded owner at all, e.g. a lock claimed before this method existed). Used by a
   * stale-generation reject so a duplicate/delayed copy can never clear a lock that a
   * newer-generation subscription has since reclaimed for this exact message — see {@link
   * io.camunda.zeebe.engine.state.immutable.MessageState#correlationOwner}.
   *
   * @return {@code true} when the lock was released, {@code false} when it was left alone because a
   *     different subscription key owns it
   */
  boolean removeMessageCorrelationOwnedBy(
      long messageKey, DirectBuffer bpmnProcessId, long subscriptionKey);

  void putActiveProcessInstance(DirectBuffer bpmnProcessId, DirectBuffer correlationKey);

  void removeActiveProcessInstance(DirectBuffer bpmnProcessId, DirectBuffer correlationKey);

  /**
   * Marks a process-correlation-key lock entry as cross-partition by recording the holder instance
   * in a parallel CF. Written on {@code P_K} when the STARTED reply from {@code P_B} is applied,
   * alongside the regular {@link #putActiveProcessInstance(DirectBuffer, DirectBuffer)} call. The
   * pull-based release loop iterates this CF (via {@link
   * MessageState#visitCrossPartitionStartLocks}) and, for each entry, polls {@code P_B} for whether
   * the holder instance is still active — the target partition is derived from {@code
   * holderProcessInstanceKey}, which encodes the partition it lives on.
   *
   * <p>Idempotent: a repeated write for the same key is a no-op overwrite, which matters because
   * the cross-partition STARTED reply can be re-delivered (the dedup on {@code P_B} re-replies the
   * same processInstanceKey on retry).
   *
   * @param bpmnProcessId the lock entry's process id
   * @param correlationKey the lock entry's correlation key
   * @param holderProcessInstanceKey the instance holding the lock, created on {@code P_B}
   * @param tenantId tenant of the holder, needed to pick up the next buffered message on release
   */
  void putCrossPartitionStartLock(
      DirectBuffer bpmnProcessId,
      DirectBuffer correlationKey,
      long holderProcessInstanceKey,
      String tenantId);

  /**
   * Removes the cross-partition message-start lock discriminator for {@code (bpmnProcessId,
   * correlationKey)}. Called on {@code P_K} when the pull-based release path learns the holder
   * instance has completed on {@code P_B}, together with {@link
   * #removeActiveProcessInstance(DirectBuffer, DirectBuffer)} which releases the underlying
   * correlation-key lock. A no-op when the entry is absent.
   *
   * @param bpmnProcessId the lock entry's process id
   * @param correlationKey the lock entry's correlation key
   */
  void removeCrossPartitionStartLock(DirectBuffer bpmnProcessId, DirectBuffer correlationKey);

  void putProcessInstanceCorrelationKey(long processInstanceKey, DirectBuffer correlationKey);

  void removeProcessInstanceCorrelationKey(long processInstanceKey);

  /**
   * Writes the cross-partition message-start holder-origin entry for a holder instance on {@code
   * P_B}, keyed by {@code processInstanceKey}. Called when {@code P_B} applies its local {@code
   * STARTED} for an instance created via the cross-partition handshake, it captures the lock
   * coordinates and the addressing {@code messageKey} so that a later completion/termination of the
   * holder can push a {@code RELEASE} to {@code P_K} without re-deriving them from the completing
   * element's context. The {@code bpmnProcessId} stored here is authoritative — a migrated holder's
   * new version may declare a different process id, but the lock on {@code P_K} is keyed by the id
   * captured at creation.
   *
   * <p>Idempotent: a repeated write for the same holder is a no-op overwrite (the {@code STARTED}
   * reply can be re-applied on retry).
   *
   * @param processInstanceKey the holder instance's process-instance key (the entry's key)
   * @param bpmnProcessId the process id of the lock to release on {@code P_K}
   * @param correlationKey the correlation key of the lock to release on {@code P_K}
   * @param tenantId tenant of the holder, carried through to the {@code RELEASE}
   * @param messageKey the buffered message's key; its partition bits address {@code P_K}
   */
  void putCrossPartitionStartHolderOrigin(
      long processInstanceKey,
      DirectBuffer bpmnProcessId,
      DirectBuffer correlationKey,
      String tenantId,
      long messageKey);

  /**
   * Removes the cross-partition message-start holder-origin entry for {@code processInstanceKey}. A
   * no-op when the entry is absent, so the push path and a reconciliation-driven cleanup can both
   * attempt removal without coordinating.
   *
   * @param processInstanceKey the holder instance's process-instance key
   */
  void removeCrossPartitionStartHolderOrigin(long processInstanceKey);

  void remove(long messageKey);
}
