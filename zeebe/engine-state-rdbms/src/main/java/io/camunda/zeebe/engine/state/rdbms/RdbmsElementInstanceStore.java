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
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.db.impl.jdbc.EntityCache;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState.TakenSequenceFlowVisitor;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.ElementInstanceStore;
import io.camunda.zeebe.engine.state.instance.RuntimeInstructions;
import io.camunda.zeebe.engine.state.rdbms.ElementInstanceMapper.ElementInstanceRow;
import io.camunda.zeebe.engine.state.rdbms.ElementInstanceMapper.PayloadRow;
import io.camunda.zeebe.engine.state.rdbms.ElementInstanceMapper.TakenSequenceFlowKey;
import io.camunda.zeebe.engine.state.rdbms.ElementInstanceMapper.TakenSequenceFlowRow;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Stores the element instance state in a relational database (PostgreSQL) instead of RocksDB.
 *
 * <p>Each table has an {@link EntityCache} that stages the writes in memory and flushes them as
 * JDBC batches when the {@link JdbcTransactionContext} commits; a failed commit keeps them staged,
 * so a retried commit writes them again. Reads look at the staged writes first, so a command sees
 * its own uncommitted changes, and fall back to the database. Queries over several rows merge the
 * committed rows with the staged writes. All SQL is in the mappers. Like the RocksDB column
 * families, every operation joins the current transaction, or runs in its own transaction
 * (committed right away) if there is none.
 *
 * <p>Staged and returned values are copies of serialized payloads, so the store never shares an
 * object with a caller: mutating an instance without calling {@link
 * #updateInstance(ElementInstance)} has no effect, and every read returns a new instance.
 *
 * <p>The process instances by definition key and the active process instance count are not kept as
 * separate data: they are derived from the element instance rows of type {@link
 * BpmnElementType#PROCESS}, whose {@code process_definition_key} column follows the instance (e.g.
 * on migration). Hence {@link #insertProcessInstanceKeyByDefinitionKey(long, long)} and {@link
 * #deleteProcessInstanceKeyByDefinitionKey(long, long)} do nothing.
 *
 * <p>Neither is the index of process instances by business id: a process instance row has its
 * record's business id as indexed column (null if empty), which follows the record when a business
 * id is assigned later or the instance is migrated to another process id. The RocksDB index only
 * has entries for root process instances, so the lookup only matches rows without parent process
 * instance, the same condition the appliers check. The engine inserts and deletes the RocksDB index
 * entries exactly when it creates (or assigns the business id of) and removes such a row, with the
 * same values, so {@link #insertProcessInstanceKeyByBusinessId} and {@link
 * #deleteProcessInstanceKeyByBusinessId} do nothing as well.
 *
 * <p>Stricter than the RocksDB store: taking a sequence flow in a flow scope that doesn't exist
 * fails, because its row needs the flow scope's process instance and tenant; decrementing a
 * sequence flow that was never taken fails with an {@link IllegalStateException}.
 *
 * <p>Every row records the process instance, root process instance and tenant it belongs to. An
 * element instance row takes them from its record. The other rows take them on insert from the
 * element instance they belong to, read through the element instance cache, so usually without a
 * query: a taken sequence flow from its flow scope. The await result metadata and runtime
 * instructions are stored while the process instance is created, before its element instance
 * exists; they belong to a root process instance and get its tenant passed.
 */
public final class RdbmsElementInstanceStore implements ElementInstanceStore {

  private static final short PROCESS_TYPE = (short) BpmnElementType.PROCESS.ordinal();

  /** See {@link ProcessInstanceRecord#hasParentProcessInstance()}. */
  private static final long NO_PARENT_PROCESS_INSTANCE = -1L;

  private static final Comparator<TakenSequenceFlowRow> BY_GATEWAY_AND_SEQUENCE_FLOW =
      Comparator.comparing(TakenSequenceFlowRow::gatewayElementId)
          .thenComparing(TakenSequenceFlowRow::sequenceFlowElementId);

  private final EngineStateRdbms rdbms;
  private final int partitionId;

  private final EntityCache<Long, ElementInstanceRow> elementInstances;
  private final EntityCache<TakenSequenceFlowKey, TakenSequenceFlowRow> takenSequenceFlows;
  private final EntityCache<Long, PayloadRow> awaitResults;
  private final EntityCache<Long, PayloadRow> runtimeInstructions;

  public RdbmsElementInstanceStore(
      final JdbcTransactionContext transactionContext, final int partitionId) {
    rdbms = new EngineStateRdbms(transactionContext, partitionId);
    this.partitionId = partitionId;

    elementInstances =
        rdbms.cache(
            "element instance",
            ElementInstanceRow::key,
            ElementInstanceMapper.class,
            ElementInstanceMapper::selectElementInstance,
            ElementInstanceMapper::insertElementInstance,
            ElementInstanceMapper::updateElementInstance,
            ElementInstanceMapper::deleteElementInstance);
    takenSequenceFlows =
        rdbms.cache(
            "taken sequence flow",
            TakenSequenceFlowRow::key,
            ElementInstanceMapper.class,
            ElementInstanceMapper::selectTakenSequenceFlow,
            ElementInstanceMapper::insertTakenSequenceFlow,
            ElementInstanceMapper::updateTakenSequenceFlow,
            ElementInstanceMapper::deleteTakenSequenceFlow);
    awaitResults =
        rdbms.cache(
            "await process instance result",
            PayloadRow::key,
            ElementInstanceMapper.class,
            ElementInstanceMapper::selectAwaitResult,
            ElementInstanceMapper::insertAwaitResult,
            ElementInstanceMapper::updateAwaitResult,
            ElementInstanceMapper::deleteAwaitResult);
    runtimeInstructions =
        rdbms.cache(
            "runtime instructions",
            PayloadRow::key,
            ElementInstanceMapper.class,
            ElementInstanceMapper::selectRuntimeInstructions,
            ElementInstanceMapper::insertRuntimeInstructions,
            ElementInstanceMapper::updateRuntimeInstructions,
            ElementInstanceMapper::deleteRuntimeInstructions);
  }

  @Override
  public @Nullable ElementInstance getInstance(final long key) {
    if (key < 0) {
      // -1 marks "no instance" so we skip the lookup
      return null;
    }
    return readInstance(key);
  }

  @Override
  public @Nullable ElementInstance getSharedInstance(final long key) {
    return readInstance(key);
  }

  @Override
  public void insertInstance(final ElementInstance instance) {
    elementInstances.insert(toRow(instance));
  }

  @Override
  public void updateInstance(final ElementInstance instance) {
    elementInstances.update(toRow(instance));
  }

  @Override
  public void deleteInstance(final ElementInstance instance) {
    elementInstances.delete(instance.getKey());
  }

  @Override
  public List<ElementInstance> getChildren(final long parentKey) {
    final List<ElementInstance> children = new ArrayList<>();
    if (parentKey >= 0 && readInstance(parentKey) != null) {
      for (final var child : readChildren(parentKey)) {
        children.add(toInstance(child));
      }
    }
    return children;
  }

  @Override
  public void forEachChild(
      final long parentKey,
      final long startAtKey,
      final BiFunction<Long, ElementInstance, Boolean> visitor,
      final Predicate<ElementInstance> filter) {
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
  }

  @Override
  public void forEachChildKey(final long parentKey, final Function<Long, Boolean> visitor) {
    final var committedKeys =
        rdbms.read(
            ElementInstanceMapper.class, mapper -> mapper.selectChildKeys(partitionId, parentKey));
    final var childKeys =
        elementInstances.queryKeys(
            committedKeys, row -> row.parentKey() == parentKey, Comparator.naturalOrder());
    for (final long childKey : childKeys) {
      if (!visitor.apply(childKey)) {
        break;
      }
    }
  }

  @Override
  public void incrementTakenSequenceFlowCount(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    final var key = takenSequenceFlowKey(flowScopeKey, gatewayElementId, sequenceFlowElementId);
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
  }

  @Override
  public void decrementTakenSequenceFlowCount(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    final var key = takenSequenceFlowKey(flowScopeKey, gatewayElementId, sequenceFlowElementId);
    final var takenSequenceFlow = takenSequenceFlows.get(key);
    if (takenSequenceFlow == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to decrement the number of taken sequence flow '%s' of gateway '%s'"
                  + " in flow scope %d, but it was never taken",
              key.sequenceFlowElementId(), key.gatewayElementId(), flowScopeKey));
    }
    decrement(takenSequenceFlow);
  }

  @Override
  public void decrementTakenSequenceFlowCounts(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    for (final var takenSequenceFlow :
        readTakenSequenceFlows(flowScopeKey, bufferAsString(gatewayElementId))) {
      decrement(takenSequenceFlow);
    }
  }

  @Override
  public void deleteTakenSequenceFlows(final long flowScopeKey) {
    for (final var takenSequenceFlow : readTakenSequenceFlows(flowScopeKey, null)) {
      takenSequenceFlows.delete(takenSequenceFlow.key());
    }
  }

  @Override
  public void visitTakenSequenceFlows(
      final long flowScopeKey, final TakenSequenceFlowVisitor visitor) {
    for (final var takenSequenceFlow : readTakenSequenceFlows(flowScopeKey, null)) {
      visitor.visit(
          flowScopeKey,
          wrapString(takenSequenceFlow.gatewayElementId()),
          wrapString(takenSequenceFlow.sequenceFlowElementId()),
          takenSequenceFlow.timesTaken());
    }
  }

  @Override
  public int countTakenSequenceFlows(final long flowScopeKey, final DirectBuffer gatewayElementId) {
    return readTakenSequenceFlows(flowScopeKey, bufferAsString(gatewayElementId)).size();
  }

  @Override
  public int countTakenSequenceFlows(final long flowScopeKey) {
    return readTakenSequenceFlows(flowScopeKey, null).size();
  }

  @Override
  public Set<DirectBuffer> getTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    final Set<DirectBuffer> sequenceFlowIds = new LinkedHashSet<>();
    for (final var takenSequenceFlow :
        readTakenSequenceFlows(flowScopeKey, bufferAsString(gatewayElementId))) {
      sequenceFlowIds.add(wrapString(takenSequenceFlow.sequenceFlowElementId()));
    }
    return sequenceFlowIds;
  }

  @Override
  public void insertAwaitResult(
      final long processInstanceKey,
      final String tenantId,
      final AwaitProcessInstanceResultMetadata metadata) {
    awaitResults.insert(
        new PayloadRow(processInstanceKey, processInstanceKey, tenantId, serialize(metadata)));
  }

  @Override
  public @Nullable AwaitProcessInstanceResultMetadata getAwaitResult(
      final long processInstanceKey) {
    final var row = awaitResults.get(processInstanceKey);
    return row == null ? null : wrapCopy(new AwaitProcessInstanceResultMetadata(), row.payload());
  }

  @Override
  public void deleteAwaitResult(final long processInstanceKey) {
    awaitResults.delete(processInstanceKey);
  }

  @Override
  public void insertRuntimeInstructions(
      final long processInstanceKey,
      final String tenantId,
      final List<ProcessInstanceCreationRuntimeInstructionValue> instructions) {
    final var runtimeInstructions = new RuntimeInstructions();
    runtimeInstructions.setRuntimeInstructions(instructions);
    this.runtimeInstructions.insert(
        new PayloadRow(
            processInstanceKey, processInstanceKey, tenantId, serialize(runtimeInstructions)));
  }

  @Override
  public @Nullable RuntimeInstructions getRuntimeInstructions(final long processInstanceKey) {
    final var row = runtimeInstructions.get(processInstanceKey);
    return row == null ? null : wrapCopy(new RuntimeInstructions(), row.payload());
  }

  @Override
  public void deleteRuntimeInstructions(final long processInstanceKey) {
    runtimeInstructions.delete(processInstanceKey);
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
  public List<Long> getProcessInstanceKeysByDefinitionKey(final long processDefinitionKey) {
    final var committedKeys =
        rdbms.read(
            ElementInstanceMapper.class,
            mapper ->
                mapper.selectProcessInstanceKeysByDefinition(partitionId, processDefinitionKey));
    return elementInstances.queryKeys(
        committedKeys,
        row -> isProcessInstanceOf(row, processDefinitionKey),
        Comparator.naturalOrder());
  }

  @Override
  public boolean hasProcessInstances(
      final long processDefinitionKey, final List<Long> excludedInstances) {
    final var stagedProcessInstances =
        elementInstances.stagedMatching(
            row ->
                isProcessInstanceOf(row, processDefinitionKey)
                    && !excludedInstances.contains(row.key()));
    if (!stagedProcessInstances.isEmpty()) {
      return true;
    }
    // staged rows are checked above
    final List<Long> excludedKeys = new ArrayList<>(excludedInstances);
    excludedKeys.addAll(elementInstances.stagedKeys());
    return !rdbms
        .read(
            ElementInstanceMapper.class,
            mapper ->
                mapper.selectAnyProcessInstanceByDefinition(
                    partitionId, processDefinitionKey, excludedKeys.toArray(Long[]::new)))
        .isEmpty();
  }

  @Override
  public void insertProcessInstanceKeyByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    // derived from the process instance's element instance row, see class javadoc
  }

  @Override
  public void deleteProcessInstanceKeyByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    // derived from the process instance's element instance row, see class javadoc
  }

  @Override
  public boolean hasProcessInstanceWithBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final Predicate<Long> ignoreWhen) {
    final var committedKeys =
        rdbms.read(
            ElementInstanceMapper.class,
            mapper ->
                mapper.selectProcessInstanceKeysByBusinessId(
                    partitionId, tenantId, processDefinitionId, businessId));
    final var processInstanceKeys =
        elementInstances.queryKeys(
            committedKeys,
            row ->
                businessId.equals(row.businessId())
                    && row.parentProcessInstanceKey() == NO_PARENT_PROCESS_INSTANCE
                    && processDefinitionId.equals(row.bpmnProcessId())
                    && tenantId.equals(row.tenantId()),
            Comparator.naturalOrder());
    for (final long processInstanceKey : processInstanceKeys) {
      if (!ignoreWhen.test(processInstanceKey)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public long getActiveProcessInstanceCount() {
    final var stagedKeys = elementInstances.stagedKeys().toArray(Long[]::new);
    final long committed =
        rdbms.read(
            ElementInstanceMapper.class,
            mapper -> mapper.countProcessInstances(partitionId, stagedKeys));
    final long staged =
        elementInstances.stagedMatching(row -> row.bpmnElementType() == PROCESS_TYPE).size();
    return committed + staged;
  }

  private static TakenSequenceFlowKey takenSequenceFlowKey(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    return new TakenSequenceFlowKey(
        flowScopeKey, bufferAsString(gatewayElementId), bufferAsString(sequenceFlowElementId));
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

  private @Nullable ElementInstance readInstance(final long key) {
    final var row = elementInstances.get(key);
    return row == null ? null : toInstance(row);
  }

  /** The children of the given parent, ordered by key. */
  private List<ElementInstanceRow> readChildren(final long parentKey) {
    final var committed =
        rdbms.read(
            ElementInstanceMapper.class, mapper -> mapper.selectChildren(partitionId, parentKey));
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
      final long flowScopeKey, final @Nullable String gatewayElementId) {
    final var committed =
        rdbms.read(
            ElementInstanceMapper.class,
            mapper -> mapper.selectTakenSequenceFlows(partitionId, flowScopeKey, gatewayElementId));
    return takenSequenceFlows.query(
        committed,
        row ->
            row.flowScopeKey() == flowScopeKey
                && (gatewayElementId == null || gatewayElementId.equals(row.gatewayElementId())),
        BY_GATEWAY_AND_SEQUENCE_FLOW);
  }

  private static ElementInstanceRow toRow(final ElementInstance instance) {
    final var value = instance.getValue();
    return new ElementInstanceRow(
        instance.getKey(),
        instance.getParentKey(),
        value.getProcessInstanceKey(),
        rootProcessInstanceKey(value),
        value.getParentProcessInstanceKey(),
        value.getProcessDefinitionKey(),
        value.getTenantId(),
        value.getBpmnProcessId(),
        businessId(value),
        value.getElementId(),
        (short) value.getBpmnElementType().ordinal(),
        instance.getState().value(),
        serialize(instance));
  }

  /**
   * The business id of a process instance (root or called), or null if it has none or the record is
   * of another element instance.
   */
  private static @Nullable String businessId(final ProcessInstanceRecord value) {
    if (value.getBpmnElementType() != BpmnElementType.PROCESS) {
      return null;
    }
    final String businessId = value.getBusinessId();
    return businessId.isEmpty() ? null : businessId;
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
}
