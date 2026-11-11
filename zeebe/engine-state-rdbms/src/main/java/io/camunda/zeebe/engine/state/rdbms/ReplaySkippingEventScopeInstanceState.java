/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.instance.EventScopeInstance;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Leaves the event scope rows alone while the replay applies events whose changes the database
 * already contains (see {@link JdbcTransactionContext#isReplayingCommittedChanges()}): the mutators
 * do nothing, and the reads see the rows in the database's later state. Otherwise, every call is
 * delegated.
 */
@NullMarked
final class ReplaySkippingEventScopeInstanceState implements MutableEventScopeInstanceState {

  private final MutableEventScopeInstanceState delegate;
  private final JdbcTransactionContext context;

  ReplaySkippingEventScopeInstanceState(
      final MutableEventScopeInstanceState delegate, final JdbcTransactionContext context) {
    this.delegate = delegate;
    this.context = context;
  }

  private boolean skip() {
    return context.isReplayingCommittedChanges();
  }

  @Override
  public void createInstance(
      final long eventScopeKey,
      final Collection<DirectBuffer> interruptingElementIds,
      final Collection<DirectBuffer> boundaryElementIds) {
    if (!skip()) {
      delegate.createInstance(eventScopeKey, interruptingElementIds, boundaryElementIds);
    }
  }

  @Override
  public void deleteInstance(final long eventScopeKey) {
    if (!skip()) {
      delegate.deleteInstance(eventScopeKey);
    }
  }

  /**
   * When skipping, returns null, as the delegate does for a scope without triggers, without reading
   * or deleting a trigger. Only the state migrations call it, which run before the replay.
   */
  @Override
  public @Nullable EventTrigger pollEventTrigger(final long eventScopeKey) {
    if (skip()) {
      return null;
    }
    return delegate.pollEventTrigger(eventScopeKey);
  }

  @Override
  public void triggerEvent(
      final long eventScopeKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey) {
    if (!skip()) {
      delegate.triggerEvent(eventScopeKey, eventKey, elementId, variables, processInstanceKey);
    }
  }

  @Override
  public void triggerStartEvent(
      final long processDefinitionKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey) {
    if (!skip()) {
      delegate.triggerStartEvent(
          processDefinitionKey, eventKey, elementId, variables, processInstanceKey);
    }
  }

  @Override
  public void deleteTrigger(final long eventScopeKey, final long eventKey) {
    if (!skip()) {
      delegate.deleteTrigger(eventScopeKey, eventKey);
    }
  }

  @Override
  public @Nullable EventScopeInstance getInstance(final long eventScopeKey) {
    return delegate.getInstance(eventScopeKey);
  }

  @Override
  public @Nullable EventTrigger peekEventTrigger(final long eventScopeKey) {
    return delegate.peekEventTrigger(eventScopeKey);
  }

  @Override
  public Optional<EventTrigger> findEventTrigger(
      final long eventScopeKey, final Predicate<EventTrigger> predicate) {
    return delegate.findEventTrigger(eventScopeKey, predicate);
  }

  @Override
  public boolean canTriggerEvent(final long eventScopeKey, final DirectBuffer elementId) {
    return delegate.canTriggerEvent(eventScopeKey, elementId);
  }
}
