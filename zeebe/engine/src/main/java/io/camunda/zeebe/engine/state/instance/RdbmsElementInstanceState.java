/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionParticipant;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Element instance state stored in a relational database (PostgreSQL) instead of RocksDB.
 *
 * <p>Writes are staged in memory, keyed by element instance key, and flushed as JDBC batches when
 * the {@link JdbcTransactionContext} commits. Reads look at the staged writes first, so a command
 * sees its own uncommitted changes, and fall back to a {@code SELECT} on the context's connection.
 * Like the RocksDB column families, every operation joins the current transaction, or runs in its
 * own transaction (committed right away) if there is none.
 *
 * <p>Staged rows hold the serialized instance, so the store never shares an object with a caller:
 * mutating an instance without calling {@link #updateInstance(ElementInstance)} has no effect, and
 * every read returns a fresh instance.
 *
 * <p>Compared to {@link DbElementInstanceState}, this state only covers the element instance itself
 * (create, update, remove, get, children). It does not yet maintain the process instance by
 * definition key index nor the active process instance counter when creating or removing a process
 * instance, and {@link #removeInstance(long)} does not delete the taken sequence flows and the
 * await result metadata of the instance. All methods for this data throw {@link
 * UnsupportedOperationException}.
 */
public final class RdbmsElementInstanceState implements MutableElementInstanceState {

  private static final String NOT_SUPPORTED =
      "Not supported by the RDBMS element instance state yet";

  private static final String SELECT_INSTANCE =
      "SELECT payload FROM element_instance WHERE partition_id = ? AND element_instance_key = ?";
  private static final String SELECT_CHILDREN =
      "SELECT element_instance_key, payload FROM element_instance"
          + " WHERE partition_id = ? AND parent_key = ? ORDER BY element_instance_key";
  private static final String SELECT_CHILD_KEYS =
      "SELECT element_instance_key FROM element_instance"
          + " WHERE partition_id = ? AND parent_key = ? ORDER BY element_instance_key";
  private static final String INSERT =
      "INSERT INTO element_instance (partition_id, element_instance_key, parent_key,"
          + " process_instance_key, process_definition_key, bpmn_element_type, state, payload)"
          + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
  private static final String UPDATE =
      "UPDATE element_instance SET parent_key = ?, process_instance_key = ?,"
          + " process_definition_key = ?, bpmn_element_type = ?, state = ?, payload = ?"
          + " WHERE partition_id = ? AND element_instance_key = ?";
  private static final String DELETE =
      "DELETE FROM element_instance WHERE partition_id = ? AND element_instance_key = ?";

  private final JdbcTransactionContext transactionContext;
  private final int partitionId;
  private final MutableVariableState variableState;
  private final Map<Long, StagedRow> stagedRows = new HashMap<>();

  public RdbmsElementInstanceState(
      final JdbcTransactionContext transactionContext,
      final int partitionId,
      final MutableVariableState variableState) {
    this.transactionContext = transactionContext;
    this.partitionId = partitionId;
    this.variableState = variableState;
    transactionContext.register(new StagedRowsParticipant());
  }

  @Override
  public ElementInstance newInstance(
      final long key, final ProcessInstanceRecord value, final ProcessInstanceIntent state) {
    return newInstance(null, key, value, state);
  }

  @Override
  public ElementInstance newInstance(
      final ElementInstance parent,
      final long key,
      final ProcessInstanceRecord value,
      final ProcessInstanceIntent state) {
    return inTransaction(
        () -> {
          final ElementInstance instance;
          if (parent == null) {
            instance = new ElementInstance(key, state, value);
          } else {
            instance = new ElementInstance(key, parent, state, value);
            updateInstance(parent);
          }
          createInstance(instance);
          return instance;
        });
  }

  @Override
  public void removeInstance(final long key) {
    inTransaction(
        () -> {
          final var instance = readInstance(key);
          if (instance == null) {
            return null;
          }
          stageDelete(key);
          variableState.removeScope(key);

          final long parent = instance.getParentKey();
          if (parent > 0) {
            final var parentInstance = readInstance(parent);
            if (parentInstance == null) {
              throw new IllegalStateException(
                  String.format(
                      "Expected to find parent instance for element instance with key %d, but none was found.",
                      parent));
            }
            parentInstance.decrementChildCount();
            updateInstance(parentInstance);
          }
          return null;
        });
  }

  @Override
  public void createInstance(final ElementInstance instance) {
    inTransaction(
        () -> {
          stageInsert(instance);
          variableState.createScope(instance.getKey(), instance.getParentKey());
          return null;
        });
  }

  @Override
  public void updateInstance(final ElementInstance scopeInstance) {
    inTransaction(
        () -> {
          stageUpdate(scopeInstance);
          return null;
        });
  }

  @Override
  public void updateInstance(final long key, final Consumer<ElementInstance> modifier) {
    inTransaction(
        () -> {
          final var scopeInstance = readInstance(key);
          modifier.accept(scopeInstance);
          updateInstance(scopeInstance);
          return null;
        });
  }

  @Override
  public void setAwaitResultRequestMetadata(
      final long processInstanceKey, final AwaitProcessInstanceResultMetadata metadata) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public void incrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public void insertProcessInstanceKeyByDefinitionKey(
      final long processInstanceKey, final long processDefinitionKey) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public void deleteProcessInstanceKeyByDefinitionKey(
      final long processInstanceKey, final long processDefinitionKey) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public void addRuntimeInstructions(
      final long processInstanceKey,
      final List<ProcessInstanceCreationRuntimeInstructionValue> runtimeInstructions) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public void removeRuntimeInstructions(final long processInstanceKey) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public void insertProcessInstanceKeyByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public void deleteProcessInstanceKeyMappingByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public ElementInstance getInstance(final long key) {
    if (key < 0) {
      // -1 marks "no instance" so we skip the lookup
      return null;
    }
    return inTransaction(() -> readInstance(key));
  }

  @Override
  public List<ElementInstance> getChildren(final long parentKey) {
    return inTransaction(
        () -> {
          final List<ElementInstance> children = new ArrayList<>();
          if (readInstanceIfNotNegative(parentKey) != null) {
            for (final byte[] payload : readChildren(parentKey, true).values()) {
              children.add(deserialize(payload));
            }
          }
          return children;
        });
  }

  @Override
  public void forEachChild(
      final long parentKey,
      final long startAtKey,
      final BiFunction<Long, ElementInstance, Boolean> visitor) {
    forEachChild(parentKey, startAtKey, visitor, (elementInstance) -> true);
  }

  @Override
  public void forEachChildKey(final long parentKey, final Function<Long, Boolean> visitor) {
    inTransaction(
        () -> {
          for (final long childKey : readChildren(parentKey, false).keySet()) {
            if (!visitor.apply(childKey)) {
              break;
            }
          }
          return null;
        });
  }

  @Override
  public void forEachChild(
      final long parentKey,
      final long startAtKey,
      final BiFunction<Long, ElementInstance, Boolean> visitor,
      final Predicate<ElementInstance> filter) {
    inTransaction(
        () -> {
          final NavigableMap<Long, byte[]> children = readChildren(parentKey, true);
          // a start key of -1 means: start at the first child of the parent
          final var fromStartKey = startAtKey == -1 ? children : children.tailMap(startAtKey, true);
          for (final var child : fromStartKey.entrySet()) {
            final var childInstance = deserialize(child.getValue());
            if (filter.test(childInstance) && !visitor.apply(child.getKey(), childInstance)) {
              break;
            }
          }
          return null;
        });
  }

  @Override
  public AwaitProcessInstanceResultMetadata getAwaitResultRequestMetadata(
      final long processInstanceKey) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public int getNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public int getNumberOfTakenSequenceFlows(final long flowScopeKey) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public Set<DirectBuffer> getTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public void visitTakenSequenceFlows(
      final long flowScopeKey, final TakenSequenceFlowVisitor visitor) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public List<Long> getProcessInstanceKeysByDefinitionKey(final long processDefinitionKey) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public boolean hasActiveProcessInstances(
      final long processDefinitionKey, final List<Long> bannedInstances) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public List<RuntimeInstructionValue> getRuntimeInstructionsForElementId(
      final long processInstanceKey, final String elementId) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public boolean hasActiveProcessInstanceWithBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final Predicate<Long> ignoreWhen) {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  @Override
  public long getActiveProcessInstanceCount() {
    throw new UnsupportedOperationException(NOT_SUPPORTED);
  }

  private void stageInsert(final ElementInstance instance) {
    final long key = instance.getKey();
    final var staged = stagedRows.get(key);
    if (staged != null && staged.operation != Operation.DELETE) {
      throw new IllegalStateException(
          String.format(
              "Expected to insert element instance with key %d, but it already exists", key));
    }
    // a staged delete means the row exists in the database: re-inserting it overwrites the row
    final var operation = staged == null ? Operation.INSERT : Operation.UPDATE;
    stagedRows.put(key, StagedRow.of(operation, instance));
  }

  private void stageUpdate(final ElementInstance instance) {
    final long key = instance.getKey();
    final var staged = stagedRows.get(key);
    if (staged != null && staged.operation == Operation.DELETE) {
      throw new IllegalStateException(
          String.format(
              "Expected to update element instance with key %d, but it was removed", key));
    }
    // an instance inserted in this transaction stays an insert: the row isn't in the database yet
    final var operation = staged == null ? Operation.UPDATE : staged.operation;
    stagedRows.put(key, StagedRow.of(operation, instance));
  }

  private void stageDelete(final long key) {
    final var staged = stagedRows.get(key);
    if (staged != null && staged.operation == Operation.INSERT) {
      // never reached the database, nothing to delete
      stagedRows.remove(key);
    } else {
      stagedRows.put(key, StagedRow.delete(key));
    }
  }

  private ElementInstance readInstanceIfNotNegative(final long key) throws SQLException {
    return key < 0 ? null : readInstance(key);
  }

  private ElementInstance readInstance(final long key) throws SQLException {
    final var staged = stagedRows.get(key);
    if (staged != null) {
      return staged.operation == Operation.DELETE ? null : deserialize(staged.payload);
    }

    try (final var statement = transactionContext.connection().prepareStatement(SELECT_INSTANCE)) {
      statement.setInt(1, partitionId);
      statement.setLong(2, key);
      try (final ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? deserialize(resultSet.getBytes(1)) : null;
      }
    }
  }

  /**
   * Returns the children of the given parent, ordered by key, merging the committed rows with the
   * staged writes. If {@code withPayload} is false, the map values are null.
   */
  private NavigableMap<Long, byte[]> readChildren(final long parentKey, final boolean withPayload)
      throws SQLException {
    final NavigableMap<Long, byte[]> children = new TreeMap<>();
    final var sql = withPayload ? SELECT_CHILDREN : SELECT_CHILD_KEYS;
    try (final var statement = transactionContext.connection().prepareStatement(sql)) {
      statement.setInt(1, partitionId);
      statement.setLong(2, parentKey);
      try (final ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          final long childKey = resultSet.getLong(1);
          if (!stagedRows.containsKey(childKey)) {
            children.put(childKey, withPayload ? resultSet.getBytes(2) : null);
          }
        }
      }
    }

    for (final var staged : stagedRows.values()) {
      if (staged.operation != Operation.DELETE && staged.parentKey == parentKey) {
        children.put(staged.key, withPayload ? staged.payload : null);
      }
    }
    return children;
  }

  private <T> T inTransaction(final SqlOperation<T> operation) {
    final List<T> result = new ArrayList<>(1);
    transactionContext.runInTransaction(() -> result.add(operation.run()));
    return result.getFirst();
  }

  private static byte[] serialize(final ElementInstance instance) {
    final var payload = new byte[instance.getLength()];
    instance.write(new UnsafeBuffer(payload), 0);
    return payload;
  }

  private static ElementInstance deserialize(final byte[] payload) {
    final var instance = new ElementInstance();
    // the instance may view its source buffer, so never let it see a staged payload
    final var copy = payload.clone();
    instance.wrap(new UnsafeBuffer(copy), 0, copy.length);
    return instance;
  }

  @FunctionalInterface
  private interface SqlOperation<T> {
    T run() throws SQLException;
  }

  private enum Operation {
    INSERT,
    UPDATE,
    DELETE
  }

  /**
   * A staged write. Besides the serialized instance, it holds the lookup columns derived from it,
   * so flushing doesn't need to deserialize the payload again.
   */
  private record StagedRow(
      Operation operation,
      long key,
      long parentKey,
      long processInstanceKey,
      long processDefinitionKey,
      short bpmnElementType,
      short state,
      byte[] payload) {

    static StagedRow of(final Operation operation, final ElementInstance instance) {
      final var value = instance.getValue();
      return new StagedRow(
          operation,
          instance.getKey(),
          instance.getParentKey(),
          value.getProcessInstanceKey(),
          value.getProcessDefinitionKey(),
          (short) value.getBpmnElementType().ordinal(),
          instance.getState().value(),
          serialize(instance));
    }

    static StagedRow delete(final long key) {
      return new StagedRow(Operation.DELETE, key, -1, -1, -1, (short) -1, (short) -1, null);
    }
  }

  private final class StagedRowsParticipant implements JdbcTransactionParticipant {

    @Override
    public void flush(final Connection connection) throws SQLException {
      if (stagedRows.isEmpty()) {
        return;
      }

      // every key has at most one staged row, so the order of the batches doesn't matter
      try (final var deletes = connection.prepareStatement(DELETE);
          final var inserts = connection.prepareStatement(INSERT);
          final var updates = connection.prepareStatement(UPDATE)) {
        boolean hasDeletes = false;
        boolean hasInserts = false;
        final List<Long> updatedKeys = new ArrayList<>();

        for (final var row : stagedRows.values()) {
          switch (row.operation) {
            case DELETE -> {
              deletes.setInt(1, partitionId);
              deletes.setLong(2, row.key);
              deletes.addBatch();
              hasDeletes = true;
            }
            case INSERT -> {
              inserts.setInt(1, partitionId);
              inserts.setLong(2, row.key);
              inserts.setLong(3, row.parentKey);
              inserts.setLong(4, row.processInstanceKey);
              inserts.setLong(5, row.processDefinitionKey);
              inserts.setShort(6, row.bpmnElementType);
              inserts.setShort(7, row.state);
              inserts.setBytes(8, row.payload);
              inserts.addBatch();
              hasInserts = true;
            }
            case UPDATE -> {
              updates.setLong(1, row.parentKey);
              updates.setLong(2, row.processInstanceKey);
              updates.setLong(3, row.processDefinitionKey);
              updates.setShort(4, row.bpmnElementType);
              updates.setShort(5, row.state);
              updates.setBytes(6, row.payload);
              updates.setInt(7, partitionId);
              updates.setLong(8, row.key);
              updates.addBatch();
              updatedKeys.add(row.key);
            }
          }
        }

        if (hasDeletes) {
          deletes.executeBatch();
        }
        if (hasInserts) {
          inserts.executeBatch();
        }
        if (!updatedKeys.isEmpty()) {
          verifyUpdated(updates, updatedKeys);
        }
      }
    }

    @Override
    public void reset() {
      stagedRows.clear();
    }

    private void verifyUpdated(final PreparedStatement updates, final List<Long> updatedKeys)
        throws SQLException {
      final int[] counts = updates.executeBatch();
      for (int i = 0; i < counts.length; i++) {
        if (counts[i] == 0) {
          throw new SQLException(
              String.format(
                  "Expected to update element instance with key %d, but it does not exist",
                  updatedKeys.get(i)));
        }
        if (counts[i] != Statement.SUCCESS_NO_INFO && counts[i] != 1) {
          throw new SQLException(
              String.format(
                  "Expected to update one element instance with key %d, but updated %d",
                  updatedKeys.get(i), counts[i]));
        }
      }
    }
  }
}
