/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceDedupState;

/**
 * Mutable view of the cross-partition message-start dedup state on {@code P_B}. The consumer that
 * drives these calls (the request processor and the PI-completion applier) is attached in a later
 * commit; this commit lands the interface and its implementation so the data shape is reviewable in
 * isolation.
 */
public interface MutableMessageStartProcessInstanceDedupState
    extends MessageStartProcessInstanceDedupState {

  /**
   * Inserts a fresh {@code ACTIVE} entry for the given {@code (processDefinitionKey, messageKey)}
   * and the reverse mapping from {@code processInstanceKey} back to that pair. Called by the
   * applier of the {@code STARTED} event on a cache miss + success outcome.
   */
  void putActive(long processDefinitionKey, long messageKey, long processInstanceKey);

  /**
   * Transitions the entry whose holder is {@code processInstanceKey} to {@code TOMBSTONE} with the
   * given {@code deletionDeadline} (epoch millis), preserving the cached process-instance key so
   * late retries from {@code P_K} can still be re-replied with the original {@code STARTED} outcome
   * until the deadline passes. Does nothing if the reverse mapping is absent (e.g., the PI was not
   * created via a cross-partition ask, or the entry has already been deleted).
   */
  void tombstoneByProcessInstanceKey(long processInstanceKey, long deletionDeadline);

  /**
   * Deletes both the forward and reverse entries for the given {@code (processDefinitionKey,
   * messageKey)}. Called by the tombstone sweeper once the deletion deadline has passed.
   */
  void delete(long processDefinitionKey, long messageKey);
}
