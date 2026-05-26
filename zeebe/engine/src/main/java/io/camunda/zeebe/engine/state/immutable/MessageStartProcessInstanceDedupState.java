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
 */
public interface MessageStartProcessInstanceDedupState {

  /**
   * Returns the dedup entry for the given {@code (processDefinitionKey, messageKey)} pair, or
   * {@code null} when no entry exists. Callers must apply any out-of-band invalidation (such as the
   * banned-PI filter) themselves; this method returns the raw entry.
   */
  MessageStartProcessInstanceDedupEntry get(long processDefinitionKey, long messageKey);

  /**
   * Visits all entries whose status is {@link
   * io.camunda.zeebe.engine.state.message.MessageStartProcessInstanceDedupStatus#TOMBSTONE} and
   * whose deletion deadline is at or before {@code now}. Intended for the scheduled tombstone sweep
   * introduced together with the consumer.
   */
  void visitTombstonesPastDeadline(long now, TombstoneVisitor visitor);

  @FunctionalInterface
  interface TombstoneVisitor {
    void visit(long processDefinitionKey, long messageKey);
  }
}
