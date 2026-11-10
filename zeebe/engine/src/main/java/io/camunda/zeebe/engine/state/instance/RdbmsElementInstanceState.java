/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionParticipant;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
 * <p>The process instances by definition key and the active process instance count are not kept as
 * separate data: they are derived from the element instance rows of type {@link
 * BpmnElementType#PROCESS}, whose {@code process_definition_key} column follows the instance (e.g.
 * on migration). Hence {@link #insertProcessInstanceKeyByDefinitionKey(long, long)} and {@link
 * #deleteProcessInstanceKeyByDefinitionKey(long, long)} do nothing.
 *
 * <p>The remaining data (taken sequence flows, await result metadata, runtime instructions, the
 * business id index) is not staged: it is written directly on the context's connection. The open
 * JDBC transaction already makes these writes visible to later reads and undoes them on rollback.
 * Caveat: a failed commit rolls back the connection but keeps only the staged rows, so retrying the
 * commit loses these direct writes. Acceptable for the PoC, not beyond it.
 */
public final class RdbmsElementInstanceState implements MutableElementInstanceState {

  /**
   * Inlined instead of bound, so the planner can use the partial index on process instances (see
   * the DDL).
   */
  private static final int PROCESS_TYPE = BpmnElementType.PROCESS.ordinal();

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

  private static final String SELECT_PROCESS_INSTANCE_KEYS_BY_DEFINITION =
      "SELECT element_instance_key FROM element_instance"
          + " WHERE partition_id = ? AND bpmn_element_type = "
          + PROCESS_TYPE
          + " AND process_definition_key = ? ORDER BY element_instance_key";
  private static final String SELECT_ANY_PROCESS_INSTANCE_BY_DEFINITION =
      "SELECT element_instance_key FROM element_instance"
          + " WHERE partition_id = ? AND bpmn_element_type = "
          + PROCESS_TYPE
          + " AND process_definition_key = ? AND element_instance_key <> ALL (?) LIMIT 1";
  private static final String COUNT_PROCESS_INSTANCES =
      "SELECT COUNT(*) FROM element_instance WHERE partition_id = ? AND bpmn_element_type = "
          + PROCESS_TYPE
          + " AND element_instance_key <> ALL (?)";

  private static final String INCREMENT_TAKEN_SEQUENCE_FLOW =
      "INSERT INTO taken_sequence_flow (partition_id, flow_scope_key, gateway_element_id,"
          + " sequence_flow_element_id, times_taken) VALUES (?, ?, ?, ?, 1)"
          + " ON CONFLICT (partition_id, flow_scope_key, gateway_element_id,"
          + " sequence_flow_element_id)"
          + " DO UPDATE SET times_taken = taken_sequence_flow.times_taken + 1";
  private static final String DECREMENT_TAKEN_SEQUENCE_FLOWS_OF_GATEWAY =
      "UPDATE taken_sequence_flow SET times_taken = times_taken - 1"
          + " WHERE partition_id = ? AND flow_scope_key = ? AND gateway_element_id = ?";
  private static final String DELETE_UNTAKEN_SEQUENCE_FLOWS_OF_GATEWAY =
      "DELETE FROM taken_sequence_flow"
          + " WHERE partition_id = ? AND flow_scope_key = ? AND gateway_element_id = ?"
          + " AND times_taken <= 0";
  private static final String DECREMENT_TAKEN_SEQUENCE_FLOW =
      "UPDATE taken_sequence_flow SET times_taken = times_taken - 1"
          + " WHERE partition_id = ? AND flow_scope_key = ? AND gateway_element_id = ?"
          + " AND sequence_flow_element_id = ?";
  private static final String DELETE_UNTAKEN_SEQUENCE_FLOW =
      "DELETE FROM taken_sequence_flow"
          + " WHERE partition_id = ? AND flow_scope_key = ? AND gateway_element_id = ?"
          + " AND sequence_flow_element_id = ? AND times_taken <= 0";
  private static final String COUNT_TAKEN_SEQUENCE_FLOWS_OF_GATEWAY =
      "SELECT COUNT(*) FROM taken_sequence_flow"
          + " WHERE partition_id = ? AND flow_scope_key = ? AND gateway_element_id = ?";
  private static final String COUNT_TAKEN_SEQUENCE_FLOWS =
      "SELECT COUNT(*) FROM taken_sequence_flow WHERE partition_id = ? AND flow_scope_key = ?";
  private static final String SELECT_TAKEN_SEQUENCE_FLOWS_OF_GATEWAY =
      "SELECT sequence_flow_element_id FROM taken_sequence_flow"
          + " WHERE partition_id = ? AND flow_scope_key = ? AND gateway_element_id = ?"
          + " ORDER BY sequence_flow_element_id";
  private static final String SELECT_TAKEN_SEQUENCE_FLOWS =
      "SELECT gateway_element_id, sequence_flow_element_id, times_taken FROM taken_sequence_flow"
          + " WHERE partition_id = ? AND flow_scope_key = ?"
          + " ORDER BY gateway_element_id, sequence_flow_element_id";
  private static final String DELETE_TAKEN_SEQUENCE_FLOWS =
      "DELETE FROM taken_sequence_flow WHERE partition_id = ? AND flow_scope_key = ?";

  private static final String INSERT_AWAIT_RESULT =
      "INSERT INTO await_process_instance_result (partition_id, process_instance_key, payload)"
          + " VALUES (?, ?, ?)";
  private static final String SELECT_AWAIT_RESULT =
      "SELECT payload FROM await_process_instance_result"
          + " WHERE partition_id = ? AND process_instance_key = ?";
  private static final String DELETE_AWAIT_RESULT =
      "DELETE FROM await_process_instance_result"
          + " WHERE partition_id = ? AND process_instance_key = ?";

  private static final String INSERT_RUNTIME_INSTRUCTIONS =
      "INSERT INTO runtime_instructions (partition_id, process_instance_key, payload)"
          + " VALUES (?, ?, ?)";
  private static final String SELECT_RUNTIME_INSTRUCTIONS =
      "SELECT payload FROM runtime_instructions"
          + " WHERE partition_id = ? AND process_instance_key = ?";
  private static final String DELETE_RUNTIME_INSTRUCTIONS =
      "DELETE FROM runtime_instructions WHERE partition_id = ? AND process_instance_key = ?";

  private static final String INSERT_BUSINESS_ID =
      "INSERT INTO process_instance_by_business_id (partition_id, business_id,"
          + " process_definition_id, tenant_id, process_instance_key) VALUES (?, ?, ?, ?, ?)";
  private static final String DELETE_BUSINESS_ID =
      "DELETE FROM process_instance_by_business_id WHERE partition_id = ? AND business_id = ?"
          + " AND process_definition_id = ? AND tenant_id = ? AND process_instance_key = ?";
  private static final String SELECT_PROCESS_INSTANCE_KEYS_BY_BUSINESS_ID =
      "SELECT process_instance_key FROM process_instance_by_business_id"
          + " WHERE partition_id = ? AND business_id = ? AND process_definition_id = ?"
          + " AND tenant_id = ? ORDER BY process_instance_key";

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
          execute(DELETE_AWAIT_RESULT, statement -> statement.setLong(2, key));
          execute(DELETE_TAKEN_SEQUENCE_FLOWS, statement -> statement.setLong(2, key));

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
    inTransaction(
        () ->
            execute(
                INSERT_AWAIT_RESULT,
                statement -> {
                  statement.setLong(2, processInstanceKey);
                  statement.setBytes(3, serialize(metadata));
                }));
  }

  @Override
  public void incrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    inTransaction(
        () ->
            execute(
                INCREMENT_TAKEN_SEQUENCE_FLOW,
                statement -> {
                  statement.setLong(2, flowScopeKey);
                  statement.setString(3, bufferAsString(gatewayElementId));
                  statement.setString(4, bufferAsString(sequenceFlowElementId));
                }));
  }

  @Override
  public void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    final StatementBinder binder =
        statement -> {
          statement.setLong(2, flowScopeKey);
          statement.setString(3, bufferAsString(gatewayElementId));
        };
    inTransaction(
        () -> {
          execute(DECREMENT_TAKEN_SEQUENCE_FLOWS_OF_GATEWAY, binder);
          return execute(DELETE_UNTAKEN_SEQUENCE_FLOWS_OF_GATEWAY, binder);
        });
  }

  @Override
  public void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    final StatementBinder binder =
        statement -> {
          statement.setLong(2, flowScopeKey);
          statement.setString(3, bufferAsString(gatewayElementId));
          statement.setString(4, bufferAsString(sequenceFlowElementId));
        };
    inTransaction(
        () -> {
          if (execute(DECREMENT_TAKEN_SEQUENCE_FLOW, binder) == 0) {
            throw new IllegalStateException(
                String.format(
                    "Expected to decrement the number of taken sequence flow '%s' of gateway '%s'"
                        + " in flow scope %d, but it was never taken",
                    bufferAsString(sequenceFlowElementId),
                    bufferAsString(gatewayElementId),
                    flowScopeKey));
          }
          return execute(DELETE_UNTAKEN_SEQUENCE_FLOW, binder);
        });
  }

  @Override
  public void insertProcessInstanceKeyByDefinitionKey(
      final long processInstanceKey, final long processDefinitionKey) {
    // derived from the process instance's element instance row, see class javadoc
  }

  @Override
  public void deleteProcessInstanceKeyByDefinitionKey(
      final long processInstanceKey, final long processDefinitionKey) {
    // derived from the process instance's element instance row, see class javadoc
  }

  @Override
  public void addRuntimeInstructions(
      final long processInstanceKey,
      final List<ProcessInstanceCreationRuntimeInstructionValue> runtimeInstructions) {
    final var instructions = new RuntimeInstructions();
    instructions.setRuntimeInstructions(runtimeInstructions);
    inTransaction(
        () ->
            execute(
                INSERT_RUNTIME_INSTRUCTIONS,
                statement -> {
                  statement.setLong(2, processInstanceKey);
                  statement.setBytes(3, serialize(instructions));
                }));
  }

  @Override
  public void removeRuntimeInstructions(final long processInstanceKey) {
    inTransaction(
        () ->
            execute(
                DELETE_RUNTIME_INSTRUCTIONS,
                statement -> statement.setLong(2, processInstanceKey)));
  }

  @Override
  public void insertProcessInstanceKeyByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    inTransaction(
        () ->
            execute(
                INSERT_BUSINESS_ID,
                bindBusinessId(businessId, processDefinitionId, tenantId, processInstanceKey)));
  }

  @Override
  public void deleteProcessInstanceKeyMappingByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    inTransaction(
        () ->
            execute(
                DELETE_BUSINESS_ID,
                bindBusinessId(businessId, processDefinitionId, tenantId, processInstanceKey)));
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
    return inTransaction(
        () -> {
          final var payload =
              querySingle(
                  SELECT_AWAIT_RESULT,
                  statement -> statement.setLong(2, processInstanceKey),
                  resultSet -> resultSet.getBytes(1));
          if (payload == null) {
            return null;
          }
          final var metadata = new AwaitProcessInstanceResultMetadata();
          metadata.wrap(new UnsafeBuffer(payload), 0, payload.length);
          return metadata;
        });
  }

  @Override
  public int getNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    return inTransaction(
        () ->
            querySingle(
                COUNT_TAKEN_SEQUENCE_FLOWS_OF_GATEWAY,
                statement -> {
                  statement.setLong(2, flowScopeKey);
                  statement.setString(3, bufferAsString(gatewayElementId));
                },
                resultSet -> resultSet.getInt(1)));
  }

  @Override
  public int getNumberOfTakenSequenceFlows(final long flowScopeKey) {
    return inTransaction(
        () ->
            querySingle(
                COUNT_TAKEN_SEQUENCE_FLOWS,
                statement -> statement.setLong(2, flowScopeKey),
                resultSet -> resultSet.getInt(1)));
  }

  @Override
  public Set<DirectBuffer> getTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    final Set<DirectBuffer> takenSequenceFlows = new LinkedHashSet<>();
    inTransaction(
        () ->
            query(
                SELECT_TAKEN_SEQUENCE_FLOWS_OF_GATEWAY,
                statement -> {
                  statement.setLong(2, flowScopeKey);
                  statement.setString(3, bufferAsString(gatewayElementId));
                },
                resultSet -> {
                  takenSequenceFlows.add(wrapString(resultSet.getString(1)));
                  return true;
                }));
    return takenSequenceFlows;
  }

  @Override
  public void visitTakenSequenceFlows(
      final long flowScopeKey, final TakenSequenceFlowVisitor visitor) {
    inTransaction(
        () ->
            query(
                SELECT_TAKEN_SEQUENCE_FLOWS,
                statement -> statement.setLong(2, flowScopeKey),
                resultSet -> {
                  visitor.visit(
                      flowScopeKey,
                      wrapString(resultSet.getString(1)),
                      wrapString(resultSet.getString(2)),
                      resultSet.getInt(3));
                  return true;
                }));
  }

  @Override
  public List<Long> getProcessInstanceKeysByDefinitionKey(final long processDefinitionKey) {
    return inTransaction(
        () -> {
          final NavigableSet<Long> processInstanceKeys = new TreeSet<>();
          query(
              SELECT_PROCESS_INSTANCE_KEYS_BY_DEFINITION,
              statement -> statement.setLong(2, processDefinitionKey),
              resultSet -> {
                final long key = resultSet.getLong(1);
                if (!stagedRows.containsKey(key)) {
                  processInstanceKeys.add(key);
                }
                return true;
              });
          for (final var staged : stagedRows.values()) {
            if (isStagedProcessInstance(staged, processDefinitionKey)) {
              processInstanceKeys.add(staged.key);
            }
          }
          return new ArrayList<>(processInstanceKeys);
        });
  }

  @Override
  public boolean hasActiveProcessInstances(
      final long processDefinitionKey, final List<Long> bannedInstances) {
    return inTransaction(
        () -> {
          for (final var staged : stagedRows.values()) {
            if (isStagedProcessInstance(staged, processDefinitionKey)
                && !bannedInstances.contains(staged.key)) {
              return true;
            }
          }
          // banned instances don't count as active, staged rows are checked above
          final List<Long> excludedKeys = new ArrayList<>(bannedInstances);
          excludedKeys.addAll(stagedRows.keySet());
          return querySingle(
                  SELECT_ANY_PROCESS_INSTANCE_BY_DEFINITION,
                  statement -> {
                    statement.setLong(2, processDefinitionKey);
                    statement.setArray(3, bigintArray(excludedKeys));
                  },
                  resultSet -> true)
              != null;
        });
  }

  @Override
  public List<RuntimeInstructionValue> getRuntimeInstructionsForElementId(
      final long processInstanceKey, final String elementId) {
    return inTransaction(
        () -> {
          final var payload =
              querySingle(
                  SELECT_RUNTIME_INSTRUCTIONS,
                  statement -> statement.setLong(2, processInstanceKey),
                  resultSet -> resultSet.getBytes(1));
          if (payload == null) {
            return List.of();
          }
          final var instructions = new RuntimeInstructions();
          instructions.wrap(new UnsafeBuffer(payload), 0, payload.length);
          return instructions.getRuntimeInstructions().stream()
              .filter(instruction -> instruction.getAfterElementId().equals(elementId))
              .toList();
        });
  }

  @Override
  public boolean hasActiveProcessInstanceWithBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final Predicate<Long> ignoreWhen) {
    return inTransaction(
        () ->
            // stops at the first instance that isn't ignored
            query(
                SELECT_PROCESS_INSTANCE_KEYS_BY_BUSINESS_ID,
                statement -> {
                  statement.setString(2, businessId);
                  statement.setString(3, processDefinitionId);
                  statement.setString(4, tenantId);
                },
                resultSet -> ignoreWhen.test(resultSet.getLong(1))));
  }

  @Override
  public long getActiveProcessInstanceCount() {
    return inTransaction(
        () -> {
          long count =
              querySingle(
                  COUNT_PROCESS_INSTANCES,
                  statement -> statement.setArray(2, bigintArray(stagedRows.keySet())),
                  resultSet -> resultSet.getLong(1));
          for (final var staged : stagedRows.values()) {
            if (staged.operation != Operation.DELETE && staged.bpmnElementType == PROCESS_TYPE) {
              count++;
            }
          }
          return count;
        });
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

  private static boolean isStagedProcessInstance(
      final StagedRow staged, final long processDefinitionKey) {
    return staged.operation != Operation.DELETE
        && staged.bpmnElementType == PROCESS_TYPE
        && staged.processDefinitionKey == processDefinitionKey;
  }

  private StatementBinder bindBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    return statement -> {
      statement.setString(2, businessId);
      statement.setString(3, processDefinitionId);
      statement.setString(4, tenantId);
      statement.setLong(5, processInstanceKey);
    };
  }

  /**
   * Runs a write on the context's connection right away, bypassing the staging. The partition id is
   * always the first parameter; the binder sets the others.
   *
   * @return the number of affected rows
   */
  private int execute(final String sql, final StatementBinder binder) throws SQLException {
    try (final var statement = transactionContext.connection().prepareStatement(sql)) {
      statement.setInt(1, partitionId);
      binder.bind(statement);
      return statement.executeUpdate();
    }
  }

  /**
   * Runs a query on the context's connection and passes each row to the visitor until it returns
   * false. The partition id is always the first parameter; the binder sets the others.
   *
   * @return true if the visitor stopped the iteration
   */
  private boolean query(final String sql, final StatementBinder binder, final RowVisitor visitor)
      throws SQLException {
    try (final var statement = transactionContext.connection().prepareStatement(sql)) {
      statement.setInt(1, partitionId);
      binder.bind(statement);
      try (final ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          if (!visitor.visit(resultSet)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /** Maps the first row of the query, or returns null if there is none. */
  private <T> T querySingle(
      final String sql, final StatementBinder binder, final RowMapper<T> mapper)
      throws SQLException {
    final List<T> result = new ArrayList<>(1);
    query(
        sql,
        binder,
        resultSet -> {
          result.add(mapper.map(resultSet));
          return false;
        });
    return result.isEmpty() ? null : result.getFirst();
  }

  private Array bigintArray(final Collection<Long> keys) throws SQLException {
    return transactionContext.connection().createArrayOf("bigint", keys.toArray());
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

  private static byte[] serialize(final DbValue value) {
    final var payload = new byte[value.getLength()];
    value.write(new UnsafeBuffer(payload), 0);
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

  @FunctionalInterface
  private interface StatementBinder {
    void bind(PreparedStatement statement) throws SQLException;
  }

  @FunctionalInterface
  private interface RowVisitor {
    /** Returns false to stop iterating. */
    boolean visit(ResultSet resultSet) throws SQLException;
  }

  @FunctionalInterface
  private interface RowMapper<T> {
    T map(ResultSet resultSet) throws SQLException;
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
