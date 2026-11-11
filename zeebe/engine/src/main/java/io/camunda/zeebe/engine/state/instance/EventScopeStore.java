/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import java.util.function.Predicate;
import org.agrona.DirectBuffer;

/**
 * Storage of event scope instances and of their event triggers, without any domain decision. The
 * triggers of a scope form a queue ordered by event key. Triggering a start event uses the process
 * definition key as scope key.
 */
public interface EventScopeStore {

  /**
   * Returns the scope instance, or {@code null} if it doesn't exist. The returned instance may be
   * backed by a buffer that is reused by the next read; copy it to keep it.
   */
  EventScopeInstance getScopeInstance(long scopeKey);

  /** Inserts a scope instance. The store doesn't retain the given instance. */
  void insertScopeInstance(long scopeKey, EventScopeInstance instance);

  /** Updates an existing scope instance. The store doesn't retain the given instance. */
  void updateScopeInstance(long scopeKey, EventScopeInstance instance);

  /** Deletes the scope instance if it exists. */
  void deleteScopeInstance(long scopeKey);

  /**
   * Inserts a trigger. The process instance key is metadata of the trigger, not part of its
   * identity.
   */
  void insertTrigger(
      long scopeKey,
      long eventKey,
      DirectBuffer elementId,
      DirectBuffer variables,
      long processInstanceKey);

  /** Deletes the trigger if it exists. */
  void deleteTrigger(long scopeKey, long eventKey);

  /** Deletes all triggers of the scope. */
  void deleteTriggers(long scopeKey);

  /** Returns a copy of the trigger with the lowest event key of the scope, or {@code null}. */
  EventTrigger peekTrigger(long scopeKey);

  /**
   * Visits the triggers of the scope in event key order until the visitor returns {@code false}.
   * The visited trigger may be backed by a buffer that is reused for the next one; copy it to keep
   * it.
   */
  void visitTriggers(long scopeKey, Predicate<EventTrigger> visitor);
}
