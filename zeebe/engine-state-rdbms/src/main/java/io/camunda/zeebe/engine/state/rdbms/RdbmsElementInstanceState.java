/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.jdbc.EntityCache;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.RuntimeInstructionValue;
import io.camunda.zeebe.engine.state.instance.RuntimeInstructions;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.BusinessIdKey;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.BusinessIdRow;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ElementInstanceRow;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.PayloadRow;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.TakenSequenceFlowKey;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.TakenSequenceFlowRow;
import io.camunda.zeebe.engine.state.rdbms.MyBatisEntityPersister.Loader;
import io.camunda.zeebe.engine.state.rdbms.MyBatisEntityPersister.Writer;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import io.camunda.zeebe.util.buffer.BufferReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Element instance state stored in a relational database (PostgreSQL) instead of RocksDB.
 *
 * <p>Each table has an {@link EntityCache} that stages the writes in memory and flushes them as
 * JDBC batches when the {@link JdbcTransactionContext} commits; a failed commit keeps them staged,
 * so a retried commit writes them again. Reads look at the staged writes first, so a command sees
 * its own uncommitted changes, and fall back to the database. Queries over several rows merge the
 * committed rows with the staged writes. All SQL is in the {@link EngineStateMapper}. Like the
 * RocksDB column families, every operation joins the current transaction, or runs in its own
 * transaction (committed right away) if there is none.
 *
 * <p>Staged and returned values are copies of serialized payloads, so the store never shares an
 * object with a caller: mutating an instance without calling {@link
 * #updateInstance(ElementInstance)} has no effect, and every read returns a fresh instance.
 *
 * <p>The process instances by definition key and the active process instance count are not kept as
 * separate data: they are derived from the element instance rows of type {@link
 * BpmnElementType#PROCESS}, whose {@code process_definition_key} column follows the instance (e.g.
 * on migration). Hence {@link #insertProcessInstanceKeyByDefinitionKey(long, long)} and {@link
 * #deleteProcessInstanceKeyByDefinitionKey(long, long)} do nothing.
 *
 * <p>Every row records the process instance, root process instance and tenant it belongs to. An
 * element instance row takes them from its record. The other rows take them on insert from the
 * element instance they belong to, read through the element instance cache, so usually without a
 * query: a taken sequence flow from its flow scope, a business id from its process instance. The
 * await result metadata and runtime instructions are stored while the process instance is created,
 * before its element instance exists; they belong to a root process instance and get its tenant
 * passed.
 */
public final class RdbmsElementInstanceState implements MutableElementInstanceState {

  private static final short PROCESS_TYPE = (short) BpmnElementType.PROCESS.ordinal();

  private static final Comparator<TakenSequenceFlowRow> BY_GATEWAY_AND_SEQUENCE_FLOW =
      Comparator.comparing(TakenSequenceFlowRow::gatewayElementId)
          .thenComparing(TakenSequenceFlowRow::sequenceFlowElementId);

  private final JdbcTransactionContext transactionContext;
  private final int partitionId;
  private final MutableVariableState variableState;

  private final EntityCache<Long, ElementInstanceRow> elementInstances;
  private final EntityCache<TakenSequenceFlowKey, TakenSequenceFlowRow> takenSequenceFlows;
  private final EntityCache<Long, PayloadRow> awaitResults;
  private final EntityCache<Long, PayloadRow> runtimeInstructions;
  private final EntityCache<BusinessIdKey, BusinessIdRow> businessIds;

  public RdbmsElementInstanceState(
      final JdbcTransactionContext transactionContext,
      final int partitionId,
      final MutableVariableState variableState) {
    this.transactionContext = transactionContext;
    this.partitionId = partitionId;
    this.variableState = variableState;

    elementInstances =
        cache(
            "element instance",
            ElementInstanceRow::key,
            EngineStateMapper::selectElementInstance,
            EngineStateMapper::insertElementInstance,
            EngineStateMapper::updateElementInstance,
            EngineStateMapper::deleteElementInstance);
    takenSequenceFlows =
        cache(
            "taken sequence flow",
            TakenSequenceFlowRow::key,
            EngineStateMapper::selectTakenSequenceFlow,
            EngineStateMapper::insertTakenSequenceFlow,
            EngineStateMapper::updateTakenSequenceFlow,
            EngineStateMapper::deleteTakenSequenceFlow);
    awaitResults =
        cache(
            "await process instance result",
            PayloadRow::key,
            EngineStateMapper::selectAwaitResult,
            EngineStateMapper::insertAwaitResult,
            EngineStateMapper::updateAwaitResult,
            EngineStateMapper::deleteAwaitResult);
    runtimeInstructions =
        cache(
            "runtime instructions",
            PayloadRow::key,
            EngineStateMapper::selectRuntimeInstructions,
            EngineStateMapper::insertRuntimeInstructions,
            EngineStateMapper::updateRuntimeInstructions,
            EngineStateMapper::deleteRuntimeInstructions);
    businessIds =
        cache(
            "process instance by business id",
            BusinessIdRow::key,
            EngineStateMapper::selectBusinessId,
            EngineStateMapper::insertBusinessId,
            EngineStateMapper::updateBusinessId,
            EngineStateMapper::deleteBusinessId);
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
          elementInstances.delete(key);
          variableState.removeScope(key);
          awaitResults.delete(key);
          for (final var takenSequenceFlow : readTakenSequenceFlows(key, null)) {
            takenSequenceFlows.delete(takenSequenceFlow.key());
          }

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
          elementInstances.insert(toRow(instance));
          variableState.createScope(instance.getKey(), instance.getParentKey());
          return null;
        });
  }

  @Override
  public void updateInstance(final ElementInstance scopeInstance) {
    inTransaction(
        () -> {
          elementInstances.update(toRow(scopeInstance));
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
      final long processInstanceKey,
      final String tenantId,
      final AwaitProcessInstanceResultMetadata metadata) {
    inTransaction(
        () -> {
          awaitResults.insert(
              new PayloadRow(
                  processInstanceKey, processInstanceKey, tenantId, serialize(metadata)));
          return null;
        });
  }

  @Override
  public void incrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    final var key =
        new TakenSequenceFlowKey(
            flowScopeKey, bufferAsString(gatewayElementId), bufferAsString(sequenceFlowElementId));
    inTransaction(
        () -> {
          final var takenSequenceFlow = takenSequenceFlows.get(key);
          if (takenSequenceFlow == null) {
            final var flowScope = elementInstances.get(flowScopeKey);
            if (flowScope == null) {
              throw new IllegalStateException(
                  String.format(
                      "Expected to take sequence flow '%s' to gateway '%s' in flow scope %d,"
                          + " but the flow scope does not exist",
                      key.sequenceFlowElementId(), key.gatewayElementId(), flowScopeKey));
            }
            takenSequenceFlows.insert(
                new TakenSequenceFlowRow(
                    key.flowScopeKey(),
                    key.gatewayElementId(),
                    key.sequenceFlowElementId(),
                    flowScope.processInstanceKey(),
                    flowScope.rootProcessInstanceKey(),
                    flowScope.tenantId(),
                    1));
          } else {
            takenSequenceFlows.update(
                takenSequenceFlow.withTimesTaken(takenSequenceFlow.timesTaken() + 1));
          }
          return null;
        });
  }

  @Override
  public void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    inTransaction(
        () -> {
          for (final var takenSequenceFlow :
              readTakenSequenceFlows(flowScopeKey, bufferAsString(gatewayElementId))) {
            decrement(takenSequenceFlow);
          }
          return null;
        });
  }

  @Override
  public void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    final var key =
        new TakenSequenceFlowKey(
            flowScopeKey, bufferAsString(gatewayElementId), bufferAsString(sequenceFlowElementId));
    inTransaction(
        () -> {
          final var takenSequenceFlow = takenSequenceFlows.get(key);
          if (takenSequenceFlow == null) {
            throw new IllegalStateException(
                String.format(
                    "Expected to decrement the number of taken sequence flow '%s' of gateway '%s'"
                        + " in flow scope %d, but it was never taken",
                    key.sequenceFlowElementId(), key.gatewayElementId(), flowScopeKey));
          }
          decrement(takenSequenceFlow);
          return null;
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
      final String tenantId,
      final List<ProcessInstanceCreationRuntimeInstructionValue> runtimeInstructions) {
    final var instructions = new RuntimeInstructions();
    instructions.setRuntimeInstructions(runtimeInstructions);
    inTransaction(
        () -> {
          this.runtimeInstructions.insert(
              new PayloadRow(
                  processInstanceKey, processInstanceKey, tenantId, serialize(instructions)));
          return null;
        });
  }

  @Override
  public void removeRuntimeInstructions(final long processInstanceKey) {
    inTransaction(
        () -> {
          runtimeInstructions.delete(processInstanceKey);
          return null;
        });
  }

  @Override
  public void insertProcessInstanceKeyByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    inTransaction(
        () -> {
          // only root process instances have a business id: without its row, it is its own root
          final var processInstance = elementInstances.get(processInstanceKey);
          final long rootProcessInstanceKey =
              processInstance == null
                  ? processInstanceKey
                  : processInstance.rootProcessInstanceKey();
          businessIds.insert(
              new BusinessIdRow(
                  businessId,
                  processDefinitionId,
                  tenantId,
                  processInstanceKey,
                  rootProcessInstanceKey));
          return null;
        });
  }

  @Override
  public void deleteProcessInstanceKeyMappingByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    inTransaction(
        () -> {
          businessIds.delete(
              new BusinessIdKey(businessId, processDefinitionId, tenantId, processInstanceKey));
          return null;
        });
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
          if (parentKey >= 0 && readInstance(parentKey) != null) {
            for (final var child : readChildren(parentKey)) {
              children.add(toInstance(child));
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
          final var committedKeys = read(mapper -> mapper.selectChildKeys(partitionId, parentKey));
          final var childKeys =
              elementInstances.queryKeys(
                  committedKeys, row -> row.parentKey() == parentKey, Comparator.naturalOrder());
          for (final long childKey : childKeys) {
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
          for (final var child : readChildren(parentKey)) {
            // a start key of -1 means: start at the first child of the parent
            if (startAtKey != -1 && child.key() < startAtKey) {
              continue;
            }
            final var childInstance = toInstance(child);
            if (filter.test(childInstance) && !visitor.apply(child.key(), childInstance)) {
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
          final var row = awaitResults.get(processInstanceKey);
          return row == null
              ? null
              : wrapCopy(new AwaitProcessInstanceResultMetadata(), row.payload());
        });
  }

  @Override
  public int getNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    return inTransaction(
        () -> readTakenSequenceFlows(flowScopeKey, bufferAsString(gatewayElementId)).size());
  }

  @Override
  public int getNumberOfTakenSequenceFlows(final long flowScopeKey) {
    return inTransaction(() -> readTakenSequenceFlows(flowScopeKey, null).size());
  }

  @Override
  public Set<DirectBuffer> getTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    return inTransaction(
        () -> {
          final Set<DirectBuffer> sequenceFlowIds = new LinkedHashSet<>();
          for (final var takenSequenceFlow :
              readTakenSequenceFlows(flowScopeKey, bufferAsString(gatewayElementId))) {
            sequenceFlowIds.add(wrapString(takenSequenceFlow.sequenceFlowElementId()));
          }
          return sequenceFlowIds;
        });
  }

  @Override
  public void visitTakenSequenceFlows(
      final long flowScopeKey, final TakenSequenceFlowVisitor visitor) {
    inTransaction(
        () -> {
          for (final var takenSequenceFlow : readTakenSequenceFlows(flowScopeKey, null)) {
            visitor.visit(
                flowScopeKey,
                wrapString(takenSequenceFlow.gatewayElementId()),
                wrapString(takenSequenceFlow.sequenceFlowElementId()),
                takenSequenceFlow.timesTaken());
          }
          return null;
        });
  }

  @Override
  public List<Long> getProcessInstanceKeysByDefinitionKey(final long processDefinitionKey) {
    return inTransaction(
        () -> {
          final var committedKeys =
              read(
                  mapper ->
                      mapper.selectProcessInstanceKeysByDefinition(
                          partitionId, processDefinitionKey));
          return elementInstances.queryKeys(
              committedKeys,
              row -> isProcessInstanceOf(row, processDefinitionKey),
              Comparator.naturalOrder());
        });
  }

  @Override
  public boolean hasActiveProcessInstances(
      final long processDefinitionKey, final List<Long> bannedInstances) {
    return inTransaction(
        () -> {
          final var stagedProcessInstances =
              elementInstances.stagedMatching(
                  row ->
                      isProcessInstanceOf(row, processDefinitionKey)
                          && !bannedInstances.contains(row.key()));
          if (!stagedProcessInstances.isEmpty()) {
            return true;
          }
          // banned instances don't count as active, staged rows are checked above
          final List<Long> excludedKeys = new ArrayList<>(bannedInstances);
          excludedKeys.addAll(elementInstances.stagedKeys());
          return !read(mapper ->
                  mapper.selectAnyProcessInstanceByDefinition(
                      partitionId, processDefinitionKey, excludedKeys.toArray(Long[]::new)))
              .isEmpty();
        });
  }

  @Override
  public List<RuntimeInstructionValue> getRuntimeInstructionsForElementId(
      final long processInstanceKey, final String elementId) {
    return inTransaction(
        () -> {
          final var row = runtimeInstructions.get(processInstanceKey);
          if (row == null) {
            return List.of();
          }
          final var instructions = wrapCopy(new RuntimeInstructions(), row.payload());
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
        () -> {
          final var committed =
              read(
                  mapper ->
                      mapper.selectBusinessIds(
                          partitionId, businessId, processDefinitionId, tenantId));
          final var entries =
              businessIds.query(
                  committed,
                  row ->
                      row.businessId().equals(businessId)
                          && row.processDefinitionId().equals(processDefinitionId)
                          && row.tenantId().equals(tenantId),
                  Comparator.comparingLong(BusinessIdRow::processInstanceKey));
          for (final var entry : entries) {
            if (!ignoreWhen.test(entry.processInstanceKey())) {
              return true;
            }
          }
          return false;
        });
  }

  @Override
  public long getActiveProcessInstanceCount() {
    return inTransaction(
        () -> {
          final var stagedKeys = elementInstances.stagedKeys().toArray(Long[]::new);
          final long committed =
              read(mapper -> mapper.countProcessInstances(partitionId, stagedKeys));
          final long staged =
              elementInstances.stagedMatching(row -> row.bpmnElementType() == PROCESS_TYPE).size();
          return committed + staged;
        });
  }

  private <K, E> EntityCache<K, E> cache(
      final String entityName,
      final Function<E, K> keyOf,
      final Loader<K, E> loader,
      final Writer<E> inserter,
      final Writer<E> updater,
      final Writer<K> deleter) {
    final var cache =
        new EntityCache<>(
            entityName,
            new MyBatisEntityPersister<>(
                transactionContext, partitionId, keyOf, loader, inserter, updater, deleter));
    transactionContext.register(cache);
    return cache;
  }

  private void decrement(final TakenSequenceFlowRow takenSequenceFlow) {
    final int timesTaken = takenSequenceFlow.timesTaken() - 1;
    if (timesTaken > 0) {
      takenSequenceFlows.update(takenSequenceFlow.withTimesTaken(timesTaken));
    } else {
      takenSequenceFlows.delete(takenSequenceFlow.key());
    }
  }

  private static boolean isProcessInstanceOf(
      final ElementInstanceRow row, final long processDefinitionKey) {
    return row.bpmnElementType() == PROCESS_TYPE
        && row.processDefinitionKey() == processDefinitionKey;
  }

  private @Nullable ElementInstance readInstance(final long key) throws SQLException {
    final var row = elementInstances.get(key);
    return row == null ? null : toInstance(row);
  }

  /** The children of the given parent, ordered by key. */
  private List<ElementInstanceRow> readChildren(final long parentKey) throws SQLException {
    final var committed = read(mapper -> mapper.selectChildren(partitionId, parentKey));
    return elementInstances.query(
        committed,
        row -> row.parentKey() == parentKey,
        Comparator.comparingLong(ElementInstanceRow::key));
  }

  /**
   * The taken sequence flows of the flow scope, ordered by gateway and sequence flow, of all
   * gateways if the gateway id is null.
   */
  private List<TakenSequenceFlowRow> readTakenSequenceFlows(
      final long flowScopeKey, final @Nullable String gatewayElementId) throws SQLException {
    final var committed =
        read(
            mapper -> mapper.selectTakenSequenceFlows(partitionId, flowScopeKey, gatewayElementId));
    return takenSequenceFlows.query(
        committed,
        row ->
            row.flowScopeKey() == flowScopeKey
                && (gatewayElementId == null || gatewayElementId.equals(row.gatewayElementId())),
        BY_GATEWAY_AND_SEQUENCE_FLOW);
  }

  private <T> T read(final EngineStateMyBatis.MapperCall<T> call) throws SQLException {
    return EngineStateMyBatis.read(transactionContext.connection(), call);
  }

  private <T> T inTransaction(final SqlOperation<T> operation) {
    final List<T> result = new ArrayList<>(1);
    transactionContext.runInTransaction(() -> result.add(operation.run()));
    return result.getFirst();
  }

  private static ElementInstanceRow toRow(final ElementInstance instance) {
    final var value = instance.getValue();
    return new ElementInstanceRow(
        instance.getKey(),
        instance.getParentKey(),
        value.getProcessInstanceKey(),
        rootProcessInstanceKey(value),
        value.getProcessDefinitionKey(),
        value.getTenantId(),
        value.getBpmnProcessId(),
        value.getElementId(),
        (short) value.getBpmnElementType().ordinal(),
        instance.getState().value(),
        serialize(instance));
  }

  /**
   * The root process instance key of the record, or its process instance key if it has none (e.g. a
   * record written before it carried the root key).
   */
  private static long rootProcessInstanceKey(final ProcessInstanceRecord value) {
    final long rootProcessInstanceKey = value.getRootProcessInstanceKey();
    return rootProcessInstanceKey > 0 ? rootProcessInstanceKey : value.getProcessInstanceKey();
  }

  private static ElementInstance toInstance(final ElementInstanceRow row) {
    return wrapCopy(new ElementInstance(), row.payload());
  }

  private static byte[] serialize(final DbValue value) {
    final var payload = new byte[value.getLength()];
    value.write(new UnsafeBuffer(payload), 0);
    return payload;
  }

  /** Reads the payload into the target, which may keep viewing it: never let it see a row's. */
  private static <T extends BufferReader> T wrapCopy(final T target, final byte[] payload) {
    final var copy = payload.clone();
    target.wrap(new UnsafeBuffer(copy), 0, copy.length);
    return target;
  }

  @FunctionalInterface
  private interface SqlOperation<T> {
    T run() throws SQLException;
  }
}
