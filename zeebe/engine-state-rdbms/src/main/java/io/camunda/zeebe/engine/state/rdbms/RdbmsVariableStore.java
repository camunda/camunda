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
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.VariableDocumentState;
import io.camunda.zeebe.engine.state.rdbms.ElementInstanceMapper.ElementInstanceRow;
import io.camunda.zeebe.engine.state.rdbms.VariableMapper.VariableDocumentStateRow;
import io.camunda.zeebe.engine.state.rdbms.VariableMapper.VariableKey;
import io.camunda.zeebe.engine.state.rdbms.VariableMapper.VariableRow;
import io.camunda.zeebe.engine.state.variable.VariableInstance;
import io.camunda.zeebe.engine.state.variable.VariableStore;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Stores the variable state in a relational database, through the partition's {@link
 * EntitySession}: the writes are staged until the commit, the reads see them.
 *
 * <p>A scope is an element instance, so there is no table of the scope hierarchy: the element
 * instance state creates a scope with its element instance and removes it with it, and the parent
 * of a scope is the {@code parent_key} of its element instance row. {@link #createScope} and {@link
 * #deleteScope} therefore write nothing. A scope without element instance (e.g. one that gets
 * variables before its element instance is activated) has no parent.
 *
 * <p>The chain of a scope comes from the element instance path of its element instance's record:
 * the last list of the path holds the element instances from the scope's process instance down to
 * the scope, so reversed it is the scope and its ancestors, nearest first. It ends at the process
 * instance, like the parent keys do, as a called process instance has no parent. A lookup along the
 * chain then reads all of its scopes with one query. The engine sets the path on every element
 * instance it activates; an element instance without one is rejected.
 *
 * <p>The session remembers what a transaction read: single variables, including missing ones, and
 * whole scopes after a document read. Repeated lookups of a command therefore need no query, and a
 * chain lookup only queries the scopes whose variable isn't known yet. The chains are remembered
 * for the transaction as well.
 *
 * <p>Every row records the process instance, root process instance and tenant of its scope. A
 * variable takes them from the record of the event that sets it, as its scope may not exist yet
 * (e.g. the variables of a process instance being created, of a multi-instance inner instance
 * before its activation, or copied into a called process instance), or else from the element
 * instance of its scope. A variable document state takes them from its scope's element instance.
 *
 * <p>Variables of a scope are visited in the order of the RocksDB keys, by length of the name's
 * UTF-8 bytes and then by those bytes, so documents list them in the same order on both stores.
 */
public final class RdbmsVariableStore implements VariableStore {

  static final EntityType<VariableKey, VariableRow> VARIABLE =
      new EntityType<>(
          VariableMapper.class, "Variable", "variable", VariableRow::key, VariableKey::scopeKey);
  static final EntityType<Long, VariableDocumentStateRow> DOCUMENT_STATE =
      new EntityType<>(
          VariableMapper.class,
          "VariableDocumentState",
          "variable document state",
          VariableDocumentStateRow::scopeKey);

  /** The order of RocksDB's variable keys: length-prefixed names, compared byte by byte. */
  private static final Comparator<byte[]> ROCKSDB_NAME_ORDER =
      Comparator.<byte[]>comparingInt(name -> name.length).thenComparing(Arrays::compareUnsigned);

  private final EntitySession session;
  private final int partitionId;

  /** The chain of each scope looked up in the transaction, nearest scope first. */
  private final Map<Long, long[]> scopeChains;

  RdbmsVariableStore(final EntitySession session) {
    this.session = session;
    partitionId = session.partitionId();
    scopeChains = session.newTransactionCache();
  }

  @Override
  public @Nullable VariableInstance getVariable(
      final long scopeKey, final DirectBuffer name, final int nameOffset, final int nameLength) {
    final var row =
        session.get(
            VARIABLE, new VariableKey(scopeKey, bufferAsString(name, nameOffset, nameLength)));
    return row == null ? null : toInstance(row);
  }

  @Override
  public @Nullable VariableInstance findVariable(
      final long scopeKey, final DirectBuffer name, final int nameOffset, final int nameLength) {
    final String variableName = bufferAsString(name, nameOffset, nameLength);
    final long[] chain = scopeChain(scopeKey);
    final List<VariableKey> keys = new ArrayList<>(chain.length);
    for (final long scope : chain) {
      keys.add(new VariableKey(scope, variableName));
    }
    for (final var row : readVariables(keys)) {
      if (row != null) {
        return toInstance(row);
      }
    }
    return null;
  }

  @Override
  public void putVariable(
      final long scopeKey,
      final DirectBuffer name,
      final int nameOffset,
      final int nameLength,
      final VariableInstance variable,
      final @Nullable VariableRecord record) {
    final String variableName = bufferAsString(name, nameOffset, nameLength);
    final VariableRow row;
    if (record != null) {
      row =
          new VariableRow(
              scopeKey,
              variableName,
              variable.getKey(),
              record.getProcessInstanceKey(),
              rootProcessInstanceKey(
                  record.getRootProcessInstanceKey(), record.getProcessInstanceKey()),
              record.getTenantId(),
              copy(variable.getValue()),
              serialize(variable.getMetadata()));
    } else {
      final var scope = existingScope(scopeKey, "set variable '%s'".formatted(variableName));
      row =
          new VariableRow(
              scopeKey,
              variableName,
              variable.getKey(),
              scope.processInstanceKey(),
              scope.rootProcessInstanceKey(),
              scope.tenantId(),
              copy(variable.getValue()),
              serialize(variable.getMetadata()));
    }
    session.upsert(VARIABLE, row);
  }

  @Override
  public void deleteVariables(final long scopeKey) {
    final var keys =
        session.queryGroupKeys(
            VARIABLE,
            scopeKey,
            VariableMapper.class,
            mapper ->
                mapper.selectVariableNames(partitionId, scopeKey).stream()
                    .map(name -> new VariableKey(scopeKey, name))
                    .toList());
    for (final var key : keys) {
      session.delete(VARIABLE, key);
    }
  }

  @Override
  public boolean visitLocalVariables(
      final long scopeKey,
      final Predicate<DirectBuffer> filter,
      final BiConsumer<DirectBuffer, VariableInstance> consumer,
      final BooleanSupplier completionCondition) {
    final var rows = readScopes(List.of(scopeKey));
    return visit(rows, filter, consumer, completionCondition);
  }

  @Override
  public void visitVariables(
      final long scopeKey,
      final @Nullable Collection<DirectBuffer> names,
      final Predicate<DirectBuffer> filter,
      final BiConsumer<DirectBuffer, VariableInstance> consumer,
      final BooleanSupplier completionCondition) {
    final long[] chain = scopeChain(scopeKey);
    final List<VariableRow> rows;
    if (names == null) {
      final List<Long> scopes = new ArrayList<>(chain.length);
      for (final long scope : chain) {
        scopes.add(scope);
      }
      rows = readScopes(scopes);
    } else {
      final var variableNames = new LinkedHashSet<String>();
      names.forEach(name -> variableNames.add(bufferAsString(name)));
      final List<VariableKey> keys = new ArrayList<>(chain.length * variableNames.size());
      for (final long scope : chain) {
        variableNames.forEach(name -> keys.add(new VariableKey(scope, name)));
      }
      rows = new ArrayList<>();
      for (final var row : readVariables(keys)) {
        if (row != null) {
          rows.add(row);
        }
      }
    }

    final Map<Long, List<VariableRow>> rowsByScope = new HashMap<>();
    for (final var row : rows) {
      rowsByScope.computeIfAbsent(row.scopeKey(), ignored -> new ArrayList<>()).add(row);
    }
    for (final long scope : chain) {
      final var rowsOfScope = rowsByScope.get(scope);
      if (rowsOfScope != null && visit(rowsOfScope, filter, consumer, completionCondition)) {
        return;
      }
      if (rowsOfScope == null && completionCondition.getAsBoolean()) {
        return;
      }
    }
  }

  /**
   * Reads the element instances of the scopes whose chain isn't known yet with one query, and then
   * the variables of all their chains with one more, unless the session knows them already. The
   * following visits of these scopes are answered from the loaded rows.
   */
  @Override
  public void prefetchVariables(
      final Collection<Long> scopeKeys, final @Nullable Collection<DirectBuffer> names) {
    final List<Long> unknownChains = new ArrayList<>();
    for (final long scopeKey : new LinkedHashSet<>(scopeKeys)) {
      if (!scopeChains.containsKey(scopeKey)) {
        unknownChains.add(scopeKey);
      }
    }
    if (!unknownChains.isEmpty()) {
      session.getAll(
          ELEMENT_INSTANCE,
          unknownChains,
          ElementInstanceMapper.class,
          (mapper, keys) -> mapper.selectElementInstances(partitionId, keys));
    }

    final var scopes = new LinkedHashSet<Long>();
    for (final long scopeKey : scopeKeys) {
      for (final long scope : scopeChain(scopeKey)) {
        scopes.add(scope);
      }
    }
    if (names == null) {
      readScopes(List.copyOf(scopes));
    } else {
      final var variableNames = new LinkedHashSet<String>();
      names.forEach(name -> variableNames.add(bufferAsString(name)));
      final List<VariableKey> keys = new ArrayList<>(scopes.size() * variableNames.size());
      for (final long scope : scopes) {
        variableNames.forEach(name -> keys.add(new VariableKey(scope, name)));
      }
      readVariables(keys);
    }
  }

  /** The parent scope is the parent of the element instance, which the engine created with it. */
  @Override
  public void createScope(final long childKey, final long parentKey) {
    // a scope looked up before its element instance existed had no chain yet
    scopeChains.remove(childKey);
  }

  /** The element instance state removes the element instance, the scope's parent with it. */
  @Override
  public void deleteScope(final long scopeKey) {
    scopeChains.remove(scopeKey);
  }

  @Override
  public long getParentScopeKey(final long childScopeKey) {
    final var scope = session.get(ELEMENT_INSTANCE, childScopeKey);
    return scope == null ? VariableState.NO_PARENT : scope.parentKey();
  }

  @Override
  public void insertDocumentState(final long key, final VariableDocumentRecord record) {
    final long scopeKey = record.getScopeKey();
    final var scope = existingScope(scopeKey, "store a variable document state");
    final var documentState = new VariableDocumentState().setKey(key).setRecord(record);
    session.insert(
        DOCUMENT_STATE,
        new VariableDocumentStateRow(
            scopeKey,
            scope.processInstanceKey(),
            scope.rootProcessInstanceKey(),
            scope.tenantId(),
            serialize(documentState)));
  }

  @Override
  public void deleteDocumentState(final long scopeKey) {
    session.delete(DOCUMENT_STATE, scopeKey);
  }

  @Override
  public @Nullable VariableDocumentState getDocumentState(final long scopeKey) {
    final var row = session.get(DOCUMENT_STATE, scopeKey);
    return row == null ? null : wrapCopy(new VariableDocumentState(), row.payload());
  }

  /** Holds no scopes, as they are element instances: empty if there are no variables. */
  @Override
  public boolean isEmpty() {
    if (!session.stagedMatching(VARIABLE, row -> true).isEmpty()) {
      return false;
    }
    final var stagedKeys = session.stagedKeys(VARIABLE);
    final var committed =
        session.read(
            VariableMapper.class,
            mapper -> mapper.selectAnyVariableKeys(partitionId, stagedKeys.size() + 1));
    return stagedKeys.containsAll(committed);
  }

  /**
   * The scope and its ancestors, nearest first; only the scope itself if it has no element
   * instance.
   */
  private long[] scopeChain(final long scopeKey) {
    final long[] known = scopeChains.get(scopeKey);
    if (known != null) {
      return known;
    }
    final var scope = session.get(ELEMENT_INSTANCE, scopeKey);
    final long[] chain = scope == null ? new long[] {scopeKey} : chainOf(scope);
    scopeChains.put(scopeKey, chain);
    return chain;
  }

  private static long[] chainOf(final ElementInstanceRow scope) {
    final var path =
        RdbmsElementInstanceStore.toInstance(scope).getValue().getElementInstancePath();
    final List<Long> instancePath = path.isEmpty() ? List.of() : path.getLast();
    if (instancePath.isEmpty() || instancePath.getLast() != scope.key()) {
      throw new IllegalStateException(
          "Expected element instance %d to have an element instance path that ends with it, but its path is %s"
              .formatted(scope.key(), path));
    }
    final long[] chain = new long[instancePath.size()];
    for (int i = 0; i < chain.length; i++) {
      chain[i] = instancePath.get(chain.length - 1 - i);
    }
    return chain;
  }

  private ElementInstanceRow existingScope(final long scopeKey, final String action) {
    final var scope = session.get(ELEMENT_INSTANCE, scopeKey);
    if (scope == null) {
      throw new IllegalStateException(
          "Expected to %s in scope %d, but the scope's element instance does not exist"
              .formatted(action, scopeKey));
    }
    return scope;
  }

  /** The current variables of the keys, in their order, null for a missing one. */
  private List<@Nullable VariableRow> readVariables(final List<VariableKey> keys) {
    return session.getAll(
        VARIABLE,
        keys,
        VariableMapper.class,
        (mapper, unknown) -> {
          final var scopes = new LinkedHashSet<Long>();
          final var names = new LinkedHashSet<String>();
          for (final var key : unknown) {
            scopes.add(key.scopeKey());
            names.add(key.name());
          }
          return mapper.selectVariablesOfScopes(
              partitionId, List.copyOf(scopes), List.copyOf(names));
        });
  }

  /** All current variables of the scopes, in no particular order. */
  private List<VariableRow> readScopes(final List<Long> scopeKeys) {
    return session.queryGroups(
        VARIABLE,
        scopeKeys,
        VariableMapper.class,
        (mapper, unknown) -> mapper.selectVariablesOfScopes(partitionId, unknown, null));
  }

  /**
   * Visits the variables of one scope like RocksDB does: in key order, testing the completion
   * condition after each one.
   *
   * @return whether the completion condition is met
   */
  private static boolean visit(
      final List<VariableRow> rowsOfScope,
      final Predicate<DirectBuffer> filter,
      final BiConsumer<DirectBuffer, VariableInstance> consumer,
      final BooleanSupplier completionCondition) {
    final List<byte[]> names = new ArrayList<>(rowsOfScope.size());
    final Map<byte[], VariableRow> rowByName = new HashMap<>();
    for (final var row : rowsOfScope) {
      final byte[] name = row.name().getBytes(StandardCharsets.UTF_8);
      names.add(name);
      rowByName.put(name, row);
    }
    names.sort(ROCKSDB_NAME_ORDER);
    for (final byte[] name : names) {
      final DirectBuffer nameBuffer = new UnsafeBuffer(name);
      if (filter.test(nameBuffer)) {
        consumer.accept(nameBuffer, toInstance(rowByName.get(name)));
      }
      if (completionCondition.getAsBoolean()) {
        return true;
      }
    }
    return completionCondition.getAsBoolean();
  }

  private static VariableInstance toInstance(final VariableRow row) {
    final var instance = new VariableInstance();
    final byte[] value = row.value().clone();
    instance.setKey(row.variableKey());
    instance.setValue(new UnsafeBuffer(value), 0, value.length);
    instance.setMetadata(wrapCopy(new VariableMetadataRecord(), row.metadata()));
    return instance;
  }

  private static byte[] copy(final DirectBuffer buffer) {
    final byte[] bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);
    return bytes;
  }

  /** Like the element instance rows: the process instance key if the record has no root key. */
  private static long rootProcessInstanceKey(
      final long rootProcessInstanceKey, final long processInstanceKey) {
    return rootProcessInstanceKey > 0 ? rootProcessInstanceKey : processInstanceKey;
  }
}
