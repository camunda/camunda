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
 * drives these calls (the request processor and the STARTED applier) is attached in a later commit;
 * this commit lands the interface and its implementation so the data shape is reviewable in
 * isolation.
 */
public interface MutableMessageStartProcessInstanceDedupState
    extends MessageStartProcessInstanceDedupState {

  /**
   * Persists a dedup entry for the given {@code (processDefinitionKey, messageKey)} with the
   * supplied {@code processInstanceKey} and {@code deletionDeadline} (epoch millis). Callers source
   * the deadline directly from the request's {@code messageDeadline} (= {@code publishTime + ttl}
   * on {@code P_K}), so the dedup row on {@code P_B} and the buffered message on {@code P_K} share
   * the same lifetime without any engine-internal time coupling. The deadline is never updated by
   * this state. {@code upsert} semantics: a fresh {@code STARTED} reply replaces any prior entry
   * for the same key (covers re-claim by a new PI after the previous holder was banned, or after
   * the previous deadline passed but the sweep had not yet run).
   */
  void put(
      long processDefinitionKey, long messageKey, long processInstanceKey, long deletionDeadline);

  /**
   * Deletes the entry for the given {@code (processDefinitionKey, messageKey)}. Called by the
   * scheduled sweeper once the deletion deadline has passed.
   */
  void delete(long processDefinitionKey, long messageKey);
}
