/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.message.MessageStartProcessInstanceDedupEntry;

/**
 * Read-only view of the cross-partition message-start dedup state on {@code P_B}. Wired into both
 * {@link ProcessingState} and {@link io.camunda.zeebe.engine.state.mutable.MutableProcessingState}
 * together with the mutable counterpart in this commit; the consumer (the request processor) is
 * attached in a later commit together with the writer that maintains the entries.
 *
 * <p>Each entry is keyed by {@code (processDefinitionKey, messageKey)}. {@code tenantId} is
 * deliberately not part of the key because deployments are tenant-scoped — a {@code
 * processDefinitionKey} lives in exactly one tenant — so adding it would be redundant.
 *
 * <p>Retention model: the value carries a {@code deletionDeadline} (epoch millis) taken directly
 * from the request's {@code messageDeadline} (= {@code publishTime + ttl} on {@code P_K}). The read
 * path treats an entry whose deadline has passed as a cache miss; a scheduled sweeper removes such
 * rows. The deadline is never updated — the row exists to bound {@code P_K}'s retry window, not to
 * track PI lifecycle.
 */
public interface MessageStartProcessInstanceDedupState {

  /**
   * Returns the dedup entry for the given {@code (processDefinitionKey, messageKey)} pair, or
   * {@code null} when no entry exists. Callers are responsible for applying their own expiry check
   * ({@code deletionDeadline > now}) and any out-of-band invalidation (such as the banned-PI
   * filter); this method returns the raw entry.
   */
  MessageStartProcessInstanceDedupEntry get(long processDefinitionKey, long messageKey);

  /**
   * Visits entries whose {@code deletionDeadline} is at or before {@code now}. The visitor returns
   * {@code true} to continue or {@code false} to stop early (e.g. on hitting a batch limit). Do not
   * mutate the column family from within the visitor — iteration is performed directly over the
   * underlying CF; deletions must be enqueued and applied after this call returns.
   *
   * @return {@code true} iff iteration stopped early because the visitor returned {@code false}
   *     (i.e. more past-deadline entries may exist beyond what was visited); {@code false} when the
   *     visitor consumed every past-deadline entry.
   */
  boolean visitExpiredEntries(long now, ExpiredEntryVisitor visitor);

  /**
   * Returns {@code true} when at least one entry's {@code deletionDeadline} is at or before {@code
   * now}. Intended as the scheduler's cheap leader-side probe before it writes a sweep trigger
   * command.
   */
  boolean hasExpiredEntry(long now);

  @FunctionalInterface
  interface ExpiredEntryVisitor {
    /**
     * @return {@code true} to continue iteration, {@code false} to stop early.
     */
    boolean visit(long processDefinitionKey, long messageKey);
  }
}
