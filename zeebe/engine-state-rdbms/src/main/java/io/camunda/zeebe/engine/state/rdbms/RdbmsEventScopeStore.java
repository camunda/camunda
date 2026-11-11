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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Stores the event scopes and their triggers in a relational database, through the partition's
 * {@link EntitySession}: the writes are staged until the commit, the reads see them.
 *
 * <p>A scope's row carries the process instance, root process instance and tenant of its element
 * instance. The activating appliers create the scope just before they create its element instance,
 * so the scope row is staged without them and completed from the element instance right before the
 * flush (see {@link EntitySession#beforeFlush}). The element instance is staged in the same
 * transaction then, so this needs no query; a scope without element instance fails the commit.
 *
 * <p>A trigger of a scope takes the root process instance and tenant from the scope (or, while that
 * is incomplete, from its element instance), and its process instance key from the call. The
 * trigger of a start event belongs to the process instance it creates, which is its own root, and
 * to the tenant of the process definition, whose tenant is remembered across transactions as it
 * never changes.
 *
 * <p>The triggers of a scope are read in event key order, in pages that only follow while the
 * visitor continues, merged with the staged writes. The committed triggers read in a transaction
 * are remembered, including that a scope has none, so repeated peeks need no query. A scope created
 * in the transaction has no committed triggers: the triggers of a scope are deleted with it, and
 * every scope is a new element instance.
 */
public final class RdbmsEventScopeStore implements EventScopeStore {

  static final EntityType<Long, EventScopeRow> EVENT_SCOPE =
      new EntityType<>(
          EventScopeMapper.class, "EventScope", "event scope", EventScopeRow::scopeKey);
  static final EntityType<EventTriggerKey, EventTriggerRow> EVENT_TRIGGER =
      new EntityType<>(
          EventScopeMapper.class, "EventTrigger", "event trigger", EventTriggerRow::key);

  /**
   * The first page of triggers. Most scopes have none or one, so two tell the common cases apart
   * without a second query; variables can be large, so pages stay small.
   */
  static final int FIRST_PAGE_SIZE = 2;

  static final int MAX_PAGE_SIZE = 256;

  private static final int PROCESS_TENANT_CACHE_SIZE = 1024;

  private static final Comparator<EventTriggerRow> BY_EVENT_KEY =
      Comparator.comparingLong(EventTriggerRow::eventKey);

  private final EntitySession session;
  private final int partitionId;

  /** The committed triggers of each scope read in the transaction. */
  private final Map<Long, CommittedTriggers> committedTriggers;

  /** The tenant of each process definition that got a start event trigger. */
  private final Map<Long, String> processTenants =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<Long, String> eldest) {
          return size() > PROCESS_TENANT_CACHE_SIZE;
        }
      };

  RdbmsEventScopeStore(final EntitySession session) {
    this.session = session;
    partitionId = session.partitionId();
    committedTriggers = session.newTransactionCache();
    session.beforeFlush(this::completeStagedScopes);
  }

  /** Returns a new instance on its own copy of the payload, safe to modify and update. */
  @Override
  public @Nullable EventScopeInstance getScopeInstance(final long scopeKey) {
    final var row = session.get(EVENT_SCOPE, scopeKey);
    return row == null ? null : wrapCopy(new EventScopeInstance(), row.payload());
  }

  @Override
  public void insertScopeInstance(final long scopeKey, final EventScopeInstance instance) {
    session.insert(
        EVENT_SCOPE,
        new EventScopeRow(
            scopeKey,
            instance.isAccepting(),
            instance.isInterrupted(),
            -1,
            -1,
            null,
            serialize(instance)));
    committedTriggers.put(scopeKey, CommittedTriggers.none());
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
    // read by the domain to check that the scope can be triggered, so no query
    final var scope = session.get(EVENT_SCOPE, scopeKey);
    final long rootProcessInstanceKey;
    final String tenantId;
    if (scope != null && scope.hasProcessInstance()) {
      rootProcessInstanceKey = scope.rootProcessInstanceKey();
      tenantId = scope.tenantId();
    } else {
      final var elementInstance = session.get(ELEMENT_INSTANCE, scopeKey);
      if (elementInstance == null) {
        throw new IllegalStateException(
            "Expected to trigger an event in scope %d, but the scope's element instance does not exist"
                .formatted(scopeKey));
      }
      rootProcessInstanceKey = elementInstance.rootProcessInstanceKey();
      tenantId = elementInstance.tenantId();
    }
    insertTriggerRow(
        scopeKey,
        eventKey,
        elementId,
        variables,
        processInstanceKey,
        rootProcessInstanceKey,
        tenantId);
  }

  @Override
  public void insertStartEventTrigger(
      final long processDefinitionKey,
      final long eventKey,
      final DirectBuffer elementId,
      final DirectBuffer variables,
      final long processInstanceKey) {
    insertTriggerRow(
        processDefinitionKey,
        eventKey,
        elementId,
        variables,
        processInstanceKey,
        processInstanceKey,
        processTenant(processDefinitionKey));
  }

  @Override
  public void deleteTrigger(final long scopeKey, final long eventKey) {
    session.delete(EVENT_TRIGGER, new EventTriggerKey(scopeKey, eventKey));
  }

  /**
   * Deletes the committed triggers by key, read with one query unless the transaction knows them
   * all already, and the staged ones.
   */
  @Override
  public void deleteTriggers(final long scopeKey) {
    session.inTransaction(
        () -> {
          final var committed = committedTriggers.get(scopeKey);
          final List<Long> committedEventKeys;
          if (committed != null && committed.complete) {
            committedEventKeys = committed.rows.stream().map(EventTriggerRow::eventKey).toList();
          } else {
            committedEventKeys =
                session.read(
                    EventScopeMapper.class,
                    mapper -> mapper.selectEventTriggerKeysOfScope(partitionId, scopeKey));
          }
          for (final long eventKey : committedEventKeys) {
            session.delete(EVENT_TRIGGER, new EventTriggerKey(scopeKey, eventKey));
          }
          for (final var staged : stagedTriggers(scopeKey)) {
            session.delete(EVENT_TRIGGER, staged.key());
          }
          // the committed ones are all deleted now
          committedTriggers.put(scopeKey, CommittedTriggers.none());
          return null;
        });
  }

  @Override
  public @Nullable EventTrigger peekTrigger(final long scopeKey) {
    final var first = visitRows(scopeKey, row -> false);
    return first == null ? null : toTrigger(first);
  }

  @Override
  public void visitTriggers(final long scopeKey, final Predicate<EventTrigger> visitor) {
    visitRows(scopeKey, row -> visitor.test(toTrigger(row)));
  }

  /**
   * Visits the current triggers of the scope in event key order: the committed ones, read in
   * growing pages while the visitor continues, without those the transaction changed, merged with
   * the staged ones up to the last committed trigger read.
   *
   * @return the trigger at which the visitor returned false, or null if it didn't
   */
  private @Nullable EventTriggerRow visitRows(
      final long scopeKey, final Predicate<EventTriggerRow> visitor) {
    return session.inTransaction(
        () -> {
          final var committed =
              committedTriggers.computeIfAbsent(scopeKey, ignored -> new CommittedTriggers());
          final var stagedKeys = session.stagedKeys(EVENT_TRIGGER);
          final var staged = stagedTriggers(scopeKey);
          long visitedUpTo = Long.MIN_VALUE;
          while (true) {
            if (!committed.complete
                && (committed.rows.isEmpty()
                    || committed.rows.getLast().eventKey() <= visitedUpTo)) {
              readNextPage(scopeKey, committed);
            }
            final long upTo =
                committed.complete || committed.rows.isEmpty()
                    ? Long.MAX_VALUE
                    : committed.rows.getLast().eventKey();
            final long from = visitedUpTo;
            final List<EventTriggerRow> page = new ArrayList<>();
            for (final var row : committed.rows) {
              if (row.eventKey() > from
                  && row.eventKey() <= upTo
                  && !stagedKeys.contains(row.key())) {
                page.add(row);
              }
            }
            for (final var row : staged) {
              if (row.eventKey() > from && row.eventKey() <= upTo) {
                page.add(row);
              }
            }
            page.sort(BY_EVENT_KEY);
            for (final var row : page) {
              if (!visitor.test(row)) {
                return row;
              }
            }
            if (upTo == Long.MAX_VALUE) {
              return null;
            }
            visitedUpTo = upTo;
          }
        });
  }

  /** Reads the committed triggers after the known ones, twice as many as known, at least two. */
  private void readNextPage(final long scopeKey, final CommittedTriggers committed) {
    final Long after = committed.rows.isEmpty() ? null : committed.rows.getLast().eventKey();
    final int limit = Math.min(Math.max(committed.rows.size(), FIRST_PAGE_SIZE), MAX_PAGE_SIZE);
    final var page =
        session.read(
            EventScopeMapper.class,
            mapper -> mapper.selectEventTriggersOfScope(partitionId, scopeKey, after, limit));
    committed.rows.addAll(page);
    committed.complete = page.size() < limit;
  }

  private List<EventTriggerRow> stagedTriggers(final long scopeKey) {
    return session.stagedMatching(EVENT_TRIGGER, row -> row.scopeKey() == scopeKey);
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

  private String processTenant(final long processDefinitionKey) {
    final var known = processTenants.get(processDefinitionKey);
    if (known != null) {
      return known;
    }
    final var process = session.get(PROCESS, processDefinitionKey);
    if (process == null) {
      throw new IllegalStateException(
          "Expected to trigger a start event of process %d, but the process does not exist"
              .formatted(processDefinitionKey));
    }
    processTenants.put(processDefinitionKey, process.tenantId());
    return process.tenantId();
  }

  /**
   * Completes the staged scopes with the process instance of their element instance, which the
   * activating applier staged right after the scope.
   */
  private void completeStagedScopes() {
    for (final var scope : session.stagedMatching(EVENT_SCOPE, row -> !row.hasProcessInstance())) {
      final var elementInstance = session.get(ELEMENT_INSTANCE, scope.scopeKey());
      if (elementInstance == null) {
        throw new IllegalStateException(
            "Expected event scope %d to belong to an element instance, but it does not exist"
                .formatted(scope.scopeKey()));
      }
      session.update(
          EVENT_SCOPE,
          scope.withProcessInstance(
              elementInstance.processInstanceKey(),
              elementInstance.rootProcessInstanceKey(),
              elementInstance.tenantId()));
    }
  }

  private static EventTrigger toTrigger(final EventTriggerRow row) {
    return new EventTrigger()
        .setEventKey(row.eventKey())
        .setElementId(wrapString(row.elementId()))
        .setVariables(new UnsafeBuffer(row.variables().clone()))
        .setProcessInstanceKey(row.processInstanceKey());
  }

  /** The committed triggers of a scope from its first on, in event key order. */
  private static final class CommittedTriggers {
    private final List<EventTriggerRow> rows = new ArrayList<>();

    /** Whether the rows are all committed triggers of the scope. */
    private boolean complete;

    static CommittedTriggers none() {
      final var none = new CommittedTriggers();
      none.complete = true;
      return none;
    }
  }
}
