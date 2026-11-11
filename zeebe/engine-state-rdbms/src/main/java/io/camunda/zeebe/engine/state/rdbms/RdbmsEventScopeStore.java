/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static io.camunda.zeebe.engine.state.rdbms.Payloads.serialize;
import static io.camunda.zeebe.engine.state.rdbms.Payloads.wrapCopy;
import static io.camunda.zeebe.engine.state.rdbms.RdbmsElementInstanceStore.ELEMENT_INSTANCE;
import static io.camunda.zeebe.engine.state.rdbms.RdbmsProcessStore.PROCESS;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.state.instance.EventScopeInstance;
import io.camunda.zeebe.engine.state.instance.EventScopeStore;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import io.camunda.zeebe.engine.state.rdbms.EventScopeMapper.EventScopeRow;
import io.camunda.zeebe.engine.state.rdbms.EventScopeMapper.EventTriggerKey;
import io.camunda.zeebe.engine.state.rdbms.EventScopeMapper.EventTriggerRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Stores the event scopes and their triggers in a relational database, through the partition's
 * {@link EntitySession}: the writes are staged until the commit, the reads see them.
 *
 * <p>A scope's row carries the process instance, root process instance and tenant of its element
 * instance, which the activating appliers create before the scope, in the same transaction; a scope
 * without element instance is rejected.
 *
 * <p>A trigger of a scope takes the root process instance and tenant from the scope, and its
 * process instance key from the call. The trigger of a start event belongs to the process instance
 * it creates, which is its own root, and to the tenant of its process definition.
 *
 * <p>The triggers of a scope are read with one query in event key order, merged with the staged
 * writes, and remembered by the session like any group of rows, so repeated reads need no query.
 */
public final class RdbmsEventScopeStore implements EventScopeStore {

  static final EntityType<Long, EventScopeRow> EVENT_SCOPE =
      new EntityType<>(
          EventScopeMapper.class, "EventScope", "event scope", EventScopeRow::scopeKey);
  static final EntityType<EventTriggerKey, EventTriggerRow> EVENT_TRIGGER =
      new EntityType<>(
          EventScopeMapper.class,
          "EventTrigger",
          "event trigger",
          EventTriggerRow::key,
          EventTriggerKey::scopeKey);

  private static final Comparator<EventTriggerRow> BY_EVENT_KEY =
      Comparator.comparingLong(EventTriggerRow::eventKey);

  private final EntitySession session;
  private final int partitionId;

  RdbmsEventScopeStore(final EntitySession session) {
    this.session = session;
    partitionId = session.partitionId();
  }

  /** Returns a new instance on its own copy of the payload, safe to modify and update. */
  @Override
  public @Nullable EventScopeInstance getScopeInstance(final long scopeKey) {
    final var row = session.get(EVENT_SCOPE, scopeKey);
    return row == null ? null : wrapCopy(new EventScopeInstance(), row.payload());
  }

  @Override
  public void insertScopeInstance(final long scopeKey, final EventScopeInstance instance) {
    final var elementInstance = session.get(ELEMENT_INSTANCE, scopeKey);
    if (elementInstance == null) {
      throw new IllegalStateException(
          "Expected event scope %d to belong to an element instance, but it does not exist"
              .formatted(scopeKey));
    }
    session.insert(
        EVENT_SCOPE,
        new EventScopeRow(
            scopeKey,
            instance.isAccepting(),
            instance.isInterrupted(),
            elementInstance.processInstanceKey(),
            elementInstance.rootProcessInstanceKey(),
            elementInstance.tenantId(),
            serialize(instance)));
  }

  @Override
  public void updateScopeInstance(final long scopeKey, final EventScopeInstance instance) {
    final var current = session.get(EVENT_SCOPE, scopeKey);
    if (current == null) {
      throw new IllegalStateException(
          "Expected to update event scope %d, but it does not exist".formatted(scopeKey));
    }
    session.update(
        EVENT_SCOPE,
        current.withInstance(
            instance.isAccepting(), instance.isInterrupted(), serialize(instance)));
  }

  @Override
  public void deleteScopeInstance(final long scopeKey) {
    session.delete(EVENT_SCOPE, scopeKey);
  }

  @Override
  public void insertTrigger(
      final long scopeKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey) {
    final var scope = session.get(EVENT_SCOPE, scopeKey);
    if (scope == null) {
      throw new IllegalStateException(
          "Expected to trigger an event in scope %d, but the scope does not exist"
              .formatted(scopeKey));
    }
    insertTriggerRow(
        scopeKey,
        eventKey,
        elementId,
        variables,
        processInstanceKey,
        scope.rootProcessInstanceKey(),
        scope.tenantId());
  }

  @Override
  public void insertStartEventTrigger(
      final long processDefinitionKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey) {
    final var process = session.get(PROCESS, processDefinitionKey);
    if (process == null) {
      throw new IllegalStateException(
          "Expected to trigger a start event of process %d, but the process does not exist"
              .formatted(processDefinitionKey));
    }
    insertTriggerRow(
        processDefinitionKey,
        eventKey,
        elementId,
        variables,
        processInstanceKey,
        processInstanceKey,
        process.tenantId());
  }

  @Override
  public void deleteTrigger(final long scopeKey, final long eventKey) {
    session.delete(EVENT_TRIGGER, new EventTriggerKey(scopeKey, eventKey));
  }

  @Override
  public void deleteTriggers(final long scopeKey) {
    final var keys =
        session.queryGroupKeys(
            EVENT_TRIGGER,
            scopeKey,
            EventScopeMapper.class,
            mapper ->
                mapper.selectEventTriggerKeysOfScope(partitionId, scopeKey).stream()
                    .map(eventKey -> new EventTriggerKey(scopeKey, eventKey))
                    .toList());
    for (final var key : keys) {
      session.delete(EVENT_TRIGGER, key);
    }
  }

  @Override
  public @Nullable EventTrigger peekTrigger(final long scopeKey) {
    final var triggers = triggersOf(scopeKey);
    return triggers.isEmpty() ? null : toTrigger(triggers.getFirst());
  }

  @Override
  public void visitTriggers(final long scopeKey, final Predicate<EventTrigger> visitor) {
    for (final var row : triggersOf(scopeKey)) {
      if (!visitor.test(toTrigger(row))) {
        return;
      }
    }
  }

  /** The current triggers of the scope in event key order. */
  private List<EventTriggerRow> triggersOf(final long scopeKey) {
    final var triggers =
        session.queryGroups(
            EVENT_TRIGGER,
            List.of(scopeKey),
            EventScopeMapper.class,
            (mapper, scopeKeys) ->
                scopeKeys.stream()
                    .flatMap(key -> mapper.selectEventTriggersOfScope(partitionId, key).stream())
                    .toList());
    final var sorted = new ArrayList<>(triggers);
    sorted.sort(BY_EVENT_KEY);
    return sorted;
  }

  private void insertTriggerRow(
      final long scopeKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey,
      final long rootProcessInstanceKey,
      final String tenantId) {
    final byte[] variableBytes = new byte[variables.capacity()];
    variables.getBytes(0, variableBytes);
    session.insert(
        EVENT_TRIGGER,
        new EventTriggerRow(
            scopeKey,
            eventKey,
            bufferAsString(elementId),
            variableBytes,
            processInstanceKey,
            rootProcessInstanceKey,
            tenantId));
  }

  private static EventTrigger toTrigger(final EventTriggerRow row) {
    return new EventTrigger()
        .setEventKey(row.eventKey())
        .setElementId(wrapString(row.elementId()))
        .setVariables(new UnsafeBuffer(row.variables().clone()))
        .setProcessInstanceKey(row.processInstanceKey());
  }
}
