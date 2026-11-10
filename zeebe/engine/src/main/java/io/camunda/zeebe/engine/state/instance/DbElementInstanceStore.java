/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState.TakenSequenceFlowVisitor;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.agrona.collections.MutableInteger;

/**
 * Stores the element instance state in RocksDB column families. Besides the element instances, it
 * keeps the parent-child relation, the process instances by process definition and the number of
 * active process instances as derived indexes.
 *
 * <p>Reads return the column family's value instances, which the next read of the same column
 * family overwrites, except for {@link #getInstance(long)}, {@link #getChildren(long)} and {@link
 * #forEachChild(long, long, BiFunction, Predicate)}, which return new instances.
 */
public final class DbElementInstanceStore implements ElementInstanceStore {

  // active process instance counter
  private static final long ACTIVE_PROCESS_INSTANCE_COUNTER_KEY = 1L;
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbForeignKey<DbLong>>, DbNil>
      parentChildColumnFamily;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbForeignKey<DbLong>> parentChildKey;
  private final DbForeignKey<DbLong> parentKey;
  private final DbLong elementInstanceKey;
  private final ElementInstance elementInstance;
  private final ColumnFamily<DbLong, ElementInstance> elementInstanceColumnFamily;
  private final AwaitProcessInstanceResultMetadata awaitResultMetadata;
  private final ColumnFamily<DbLong, AwaitProcessInstanceResultMetadata>
      awaitProcessInstanceResultMetadataColumnFamily;
  private final DbLong flowScopeKey = new DbLong();
  private final DbString gatewayElementId = new DbString();
  private final DbString sequenceFlowElementId = new DbString();
  private final DbInt numberOfTakenSequenceFlows = new DbInt();
  private final DbCompositeKey<DbLong, DbString> flowScopeKeyAndElementId;
  private final DbCompositeKey<DbCompositeKey<DbLong, DbString>, DbString>
      numberOfTakenSequenceFlowsKey;

  /**
   * [flow scope key | gateway element id | sequence flow id] => [times the sequence flow was taken]
   */
  private final ColumnFamily<DbCompositeKey<DbCompositeKey<DbLong, DbString>, DbString>, DbInt>
      numberOfTakenSequenceFlowsColumnFamily;

  private final DbLong processDefinitionKey;
  private final DbCompositeKey<DbLong, DbLong> processInstanceKeyByProcessDefinitionKey;

  /** [process definition key | process instance key] => [Nil] */
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil>
      processInstanceKeyByProcessDefinitionKeyColumnFamily;

  private final RuntimeInstructions runtimeInstructions;
  private final ColumnFamily<DbLong, RuntimeInstructions> runtimeInstructionsByProcessInstanceKey;

  // Business ID index: [businessId | processDefinitionId | tenantId | processInstanceKey] => NIL
  private final DbString businessId = new DbString();
  private final DbString processDefinitionId = new DbString();
  private final DbString tenantId = new DbString();
  private final DbLong processInstanceKey = new DbLong();
  private final BusinessIdIndexKey businessIdIndexKey;
  private final ProcessInstanceByBusinessIdIndexKey processInstanceByBusinessIdIndexKey;
  private final ColumnFamily<ProcessInstanceByBusinessIdIndexKey, DbNil>
      processInstanceByBusinessIdIndexKeyColumnFamily;
  private final DbLong activeProcessInstanceCounterKey;
  private final DbLong activeProcessInstanceCounterValue;
  private final ColumnFamily<DbLong, DbLong> activeProcessInstanceCounterColumnFamily;

  public DbElementInstanceStore(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    elementInstanceKey = new DbLong();
    parentKey =
        new DbForeignKey<>(
            new DbLong(),
            ZbColumnFamilies.ELEMENT_INSTANCE_KEY,
            MatchType.Full,
            (k) -> k.getValue() == -1);
    parentChildKey =
        new DbCompositeKey<>(
            parentKey,
            new DbForeignKey<>(elementInstanceKey, ZbColumnFamilies.ELEMENT_INSTANCE_KEY));
    parentChildColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD,
            transactionContext,
            parentChildKey,
            DbNil.INSTANCE);

    elementInstance = new ElementInstance();
    elementInstanceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_INSTANCE_KEY,
            transactionContext,
            elementInstanceKey,
            elementInstance);

    awaitResultMetadata = new AwaitProcessInstanceResultMetadata();
    awaitProcessInstanceResultMetadataColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AWAIT_WORKLOW_RESULT,
            transactionContext,
            elementInstanceKey,
            awaitResultMetadata);

    flowScopeKeyAndElementId = new DbCompositeKey<>(flowScopeKey, gatewayElementId);
    numberOfTakenSequenceFlowsKey =
        new DbCompositeKey<>(flowScopeKeyAndElementId, sequenceFlowElementId);
    numberOfTakenSequenceFlowsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.NUMBER_OF_TAKEN_SEQUENCE_FLOWS,
            transactionContext,
            numberOfTakenSequenceFlowsKey,
            numberOfTakenSequenceFlows);

    processDefinitionKey = new DbLong();
    processInstanceKeyByProcessDefinitionKey =
        new DbCompositeKey<>(processDefinitionKey, elementInstanceKey);
    processInstanceKeyByProcessDefinitionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY,
            transactionContext,
            processInstanceKeyByProcessDefinitionKey,
            DbNil.INSTANCE);

    runtimeInstructions = new RuntimeInstructions();

    runtimeInstructionsByProcessInstanceKey =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RUNTIME_INSTRUCTIONS,
            transactionContext,
            elementInstanceKey,
            runtimeInstructions);

    businessIdIndexKey = new BusinessIdIndexKey(businessId, processDefinitionId, tenantId);
    processInstanceByBusinessIdIndexKey =
        new ProcessInstanceByBusinessIdIndexKey(businessIdIndexKey, processInstanceKey);
    processInstanceByBusinessIdIndexKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_BUSINESS_ID,
            transactionContext,
            processInstanceByBusinessIdIndexKey,
            DbNil.INSTANCE);

    activeProcessInstanceCounterKey = new DbLong();
    activeProcessInstanceCounterKey.wrapLong(ACTIVE_PROCESS_INSTANCE_COUNTER_KEY);
    activeProcessInstanceCounterValue = new DbLong();
    activeProcessInstanceCounterColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ACTIVE_PROCESS_INSTANCE_COUNT,
            transactionContext,
            activeProcessInstanceCounterKey,
            activeProcessInstanceCounterValue);
    initializeActiveProcessInstanceCounterIfNeeded();
  }

  @Override
  public ElementInstance getInstance(final long key) {
    if (key < 0) {
      // -1 marks "no instance" so we skip the lookup
      return null;
    }
    elementInstanceKey.wrapLong(key);
    return elementInstanceColumnFamily.get(elementInstanceKey, ElementInstance::new);
  }

  @Override
  public ElementInstance getSharedInstance(final long key) {
    elementInstanceKey.wrapLong(key);
    return elementInstanceColumnFamily.get(elementInstanceKey);
  }

  @Override
  public void insertInstance(final ElementInstance instance) {
    elementInstanceKey.wrapLong(instance.getKey());
    parentKey.inner().wrapLong(instance.getParentKey());

    elementInstanceColumnFamily.insert(elementInstanceKey, instance);
    parentChildColumnFamily.insert(parentChildKey, DbNil.INSTANCE);

    final var recordValue = instance.getValue();
    if (recordValue.getBpmnElementType() == BpmnElementType.PROCESS) {
      processDefinitionKey.wrapLong(recordValue.getProcessDefinitionKey());
      processInstanceKeyByProcessDefinitionKeyColumnFamily.insert(
          processInstanceKeyByProcessDefinitionKey, DbNil.INSTANCE);
      incrementActiveProcessInstanceCount();
    }
  }

  @Override
  public void updateInstance(final ElementInstance instance) {
    elementInstanceKey.wrapLong(instance.getKey());
    parentKey.inner().wrapLong(instance.getParentKey());
    elementInstanceColumnFamily.update(elementInstanceKey, instance);
  }

  @Override
  public void deleteInstance(final ElementInstance instance) {
    elementInstanceKey.wrapLong(instance.getKey());
    parentKey.inner().wrapLong(instance.getParentKey());
    parentChildColumnFamily.deleteIfExists(parentChildKey);
    elementInstanceColumnFamily.deleteExisting(elementInstanceKey);

    final var recordValue = instance.getValue();
    if (recordValue.getBpmnElementType() == BpmnElementType.PROCESS) {
      processDefinitionKey.wrapLong(recordValue.getProcessDefinitionKey());
      processInstanceKeyByProcessDefinitionKeyColumnFamily.deleteExisting(
          processInstanceKeyByProcessDefinitionKey);
      decrementActiveProcessInstanceCount();
    }
  }

  @Override
  public List<ElementInstance> getChildren(final long parentKey) {
    final List<ElementInstance> children = new ArrayList<>();
    final ElementInstance parentInstance = getInstance(parentKey);
    if (parentInstance != null) {
      this.parentKey.inner().wrapLong(parentKey);

      parentChildColumnFamily.whileEqualPrefix(
          this.parentKey,
          key -> {
            final var childKey = key.second().inner();
            final var childInstance = getInstance(childKey.getValue());
            children.add(childInstance);
          });
    }
    return children;
  }

  @Override
  public void forEachChild(
      final long parentKey,
      final long startAtKey,
      final BiFunction<Long, ElementInstance, Boolean> visitor,
      final Predicate<ElementInstance> filter) {
    this.parentKey.inner().wrapLong(parentKey);
    elementInstanceKey.wrapLong(startAtKey);

    // If startAtKey is a negative value we should use null instead. This will make it so we start
    // the iteration at the first child of the parent.
    final var compositeKey = startAtKey == -1 ? null : parentChildKey;

    parentChildColumnFamily.whileEqualPrefix(
        this.parentKey,
        compositeKey,
        key -> {
          final DbLong childKey = key.second().inner();
          final ElementInstance childInstance = getInstance(childKey.getValue());

          if (filter.test(childInstance)) {
            return visitor.apply(childKey.getValue(), childInstance);
          }
          return true;
        });
  }

  @Override
  public void forEachChildKey(final long parentKey, final Function<Long, Boolean> visitor) {
    this.parentKey.inner().wrapLong(parentKey);
    parentChildColumnFamily.whileEqualPrefix(
        this.parentKey,
        key -> {
          final var childKey = key.second().inner();
          return visitor.apply(childKey.getValue());
        });
  }

  @Override
  public void incrementTakenSequenceFlowCount(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    this.flowScopeKey.wrapLong(flowScopeKey);
    this.gatewayElementId.wrapBuffer(gatewayElementId);
    this.sequenceFlowElementId.wrapBuffer(sequenceFlowElementId);

    final var number = numberOfTakenSequenceFlowsColumnFamily.get(numberOfTakenSequenceFlowsKey);

    var newValue = 1;
    if (number != null) {
      newValue = number.getValue() + 1;
    }
    numberOfTakenSequenceFlows.wrapInt(newValue);

    numberOfTakenSequenceFlowsColumnFamily.upsert(
        numberOfTakenSequenceFlowsKey, numberOfTakenSequenceFlows);
  }

  @Override
  public void decrementTakenSequenceFlowCount(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    this.flowScopeKey.wrapLong(flowScopeKey);
    this.gatewayElementId.wrapBuffer(gatewayElementId);
    this.sequenceFlowElementId.wrapBuffer(sequenceFlowElementId);

    final var number = numberOfTakenSequenceFlowsColumnFamily.get(numberOfTakenSequenceFlowsKey);

    final var newValue = number.getValue() - 1;
    if (newValue > 0) {
      numberOfTakenSequenceFlows.wrapInt(newValue);
      numberOfTakenSequenceFlowsColumnFamily.update(
          numberOfTakenSequenceFlowsKey, numberOfTakenSequenceFlows);
    } else {
      numberOfTakenSequenceFlowsColumnFamily.deleteExisting(numberOfTakenSequenceFlowsKey);
    }
  }

  @Override
  public void decrementTakenSequenceFlowCounts(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    this.flowScopeKey.wrapLong(flowScopeKey);
    this.gatewayElementId.wrapBuffer(gatewayElementId);

    numberOfTakenSequenceFlowsColumnFamily.whileEqualPrefix(
        flowScopeKeyAndElementId,
        (key, number) -> {
          final var newValue = number.getValue() - 1;
          if (newValue > 0) {
            numberOfTakenSequenceFlows.wrapInt(newValue);
            numberOfTakenSequenceFlowsColumnFamily.update(key, numberOfTakenSequenceFlows);
          } else {
            numberOfTakenSequenceFlowsColumnFamily.deleteExisting(key);
          }
        });
  }

  @Override
  public void deleteTakenSequenceFlows(final long flowScopeKey) {
    this.flowScopeKey.wrapLong(flowScopeKey);

    numberOfTakenSequenceFlowsColumnFamily.whileEqualPrefix(
        this.flowScopeKey, numberOfTakenSequenceFlowsColumnFamily::deleteExisting);
  }

  @Override
  public void visitTakenSequenceFlows(
      final long flowScopeKey, final TakenSequenceFlowVisitor visitor) {
    this.flowScopeKey.wrapLong(flowScopeKey);
    numberOfTakenSequenceFlowsColumnFamily.whileEqualPrefix(
        this.flowScopeKey,
        (key, number) -> {
          visitor.visit(
              key.first().first().getValue(),
              key.first().second().getBuffer(),
              key.second().getBuffer(),
              number.getValue());
        });
  }

  @Override
  public int countTakenSequenceFlows(final long flowScopeKey, final DirectBuffer gatewayElementId) {
    this.flowScopeKey.wrapLong(flowScopeKey);
    this.gatewayElementId.wrapBuffer(gatewayElementId);

    return (int) numberOfTakenSequenceFlowsColumnFamily.countEqualPrefix(flowScopeKeyAndElementId);
  }

  @Override
  public int countTakenSequenceFlows(final long flowScopeKey) {
    this.flowScopeKey.wrapLong(flowScopeKey);

    final var count = new MutableInteger(0);
    numberOfTakenSequenceFlowsColumnFamily.whileEqualPrefix(
        this.flowScopeKey,
        key -> {
          count.increment();
        });

    return count.get();
  }

  @Override
  public Set<DirectBuffer> getTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    this.flowScopeKey.wrapLong(flowScopeKey);
    this.gatewayElementId.wrapBuffer(gatewayElementId);

    final Set<DirectBuffer> takenSequenceFlows = new LinkedHashSet<>();
    numberOfTakenSequenceFlowsColumnFamily.whileEqualPrefix(
        flowScopeKeyAndElementId,
        key -> {
          takenSequenceFlows.add(BufferUtil.cloneBuffer(key.second().getBuffer()));
        });

    return takenSequenceFlows;
  }

  @Override
  public void insertAwaitResult(
      final long processInstanceKey,
      final String tenantId,
      final AwaitProcessInstanceResultMetadata metadata) {
    elementInstanceKey.wrapLong(processInstanceKey);
    awaitProcessInstanceResultMetadataColumnFamily.insert(elementInstanceKey, metadata);
  }

  @Override
  public AwaitProcessInstanceResultMetadata getAwaitResult(final long processInstanceKey) {
    elementInstanceKey.wrapLong(processInstanceKey);
    return awaitProcessInstanceResultMetadataColumnFamily.get(elementInstanceKey);
  }

  @Override
  public void deleteAwaitResult(final long processInstanceKey) {
    elementInstanceKey.wrapLong(processInstanceKey);
    awaitProcessInstanceResultMetadataColumnFamily.deleteIfExists(elementInstanceKey);
  }

  @Override
  public void insertRuntimeInstructions(
      final long processInstanceKey,
      final String tenantId,
      final List<ProcessInstanceCreationRuntimeInstructionValue> instructions) {
    runtimeInstructions.setRuntimeInstructions(instructions);
    elementInstanceKey.wrapLong(processInstanceKey);
    runtimeInstructionsByProcessInstanceKey.insert(elementInstanceKey, runtimeInstructions);
  }

  @Override
  public RuntimeInstructions getRuntimeInstructions(final long processInstanceKey) {
    elementInstanceKey.wrapLong(processInstanceKey);
    return runtimeInstructionsByProcessInstanceKey.get(elementInstanceKey);
  }

  @Override
  public void deleteRuntimeInstructions(final long processInstanceKey) {
    elementInstanceKey.wrapLong(processInstanceKey);
    runtimeInstructionsByProcessInstanceKey.deleteIfExists(elementInstanceKey);
  }

  @Override
  public void insertProcessInstanceKeyByDefinitionKey(
      final long processInstanceKey, final long processDefinitionKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    elementInstanceKey.wrapLong(processInstanceKey);
    processInstanceKeyByProcessDefinitionKeyColumnFamily.insert(
        processInstanceKeyByProcessDefinitionKey, DbNil.INSTANCE);
  }

  @Override
  public void deleteProcessInstanceKeyByDefinitionKey(
      final long processInstanceKey, final long processDefinitionKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    elementInstanceKey.wrapLong(processInstanceKey);
    processInstanceKeyByProcessDefinitionKeyColumnFamily.deleteExisting(
        processInstanceKeyByProcessDefinitionKey);
  }

  @Override
  public List<Long> getProcessInstanceKeysByDefinitionKey(final long processDefinitionKey) {
    final List<Long> processInstanceKeys = new ArrayList<>();
    this.processDefinitionKey.wrapLong(processDefinitionKey);

    processInstanceKeyByProcessDefinitionKeyColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        key -> {
          final DbLong processInstanceKey = key.second();
          processInstanceKeys.add(processInstanceKey.getValue());
        });
    return processInstanceKeys;
  }

  @Override
  public boolean hasProcessInstances(
      final long processDefinitionKey, final List<Long> excludedInstances) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    final AtomicBoolean hasInstances = new AtomicBoolean(false);

    processInstanceKeyByProcessDefinitionKeyColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        key -> {
          if (excludedInstances.contains(key.second().getValue())) {
            return true;
          }

          hasInstances.set(true);
          return false;
        });

    return hasInstances.get();
  }

  @Override
  public void insertProcessInstanceKeyByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    this.businessId.wrapString(businessId);
    this.processDefinitionId.wrapString(processDefinitionId);
    this.tenantId.wrapString(tenantId);
    this.processInstanceKey.wrapLong(processInstanceKey);

    processInstanceByBusinessIdIndexKeyColumnFamily.insert(
        processInstanceByBusinessIdIndexKey, DbNil.INSTANCE);
  }

  @Override
  public void deleteProcessInstanceKeyByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    this.businessId.wrapString(businessId);
    this.processDefinitionId.wrapString(processDefinitionId);
    this.tenantId.wrapString(tenantId);
    this.processInstanceKey.wrapLong(processInstanceKey);

    processInstanceByBusinessIdIndexKeyColumnFamily.deleteExisting(
        processInstanceByBusinessIdIndexKey);
  }

  @Override
  public boolean hasProcessInstanceWithBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final Predicate<Long> ignoreWhen) {
    this.businessId.wrapString(businessId);
    this.processDefinitionId.wrapString(processDefinitionId);
    this.tenantId.wrapString(tenantId);
    final var exists = new AtomicBoolean(false);
    processInstanceByBusinessIdIndexKeyColumnFamily.whileEqualPrefix(
        businessIdIndexKey,
        key -> {
          if (ignoreWhen.test(key.processInstanceKey())) {
            return true;
          }
          exists.set(true);
          // just find the first, and then stop iterating
          return false;
        });
    return exists.get();
  }

  @Override
  public long getActiveProcessInstanceCount() {
    final var counter =
        activeProcessInstanceCounterColumnFamily.get(activeProcessInstanceCounterKey);
    return counter != null ? counter.getValue() : 0L;
  }

  private void incrementActiveProcessInstanceCount() {
    final var currentValue =
        activeProcessInstanceCounterColumnFamily.get(activeProcessInstanceCounterKey);

    final long newValue = currentValue.getValue() + 1;

    activeProcessInstanceCounterValue.wrapLong(newValue);
    activeProcessInstanceCounterColumnFamily.update(
        activeProcessInstanceCounterKey, activeProcessInstanceCounterValue);
  }

  private void decrementActiveProcessInstanceCount() {
    final var currentValue =
        activeProcessInstanceCounterColumnFamily.get(activeProcessInstanceCounterKey);

    activeProcessInstanceCounterValue.wrapLong(currentValue.getValue() - 1);
    activeProcessInstanceCounterColumnFamily.update(
        activeProcessInstanceCounterKey, activeProcessInstanceCounterValue);
  }

  /** Initializes the root process instance counter if it hasn't been initialized yet. */
  private void initializeActiveProcessInstanceCounterIfNeeded() {
    final var currentValue =
        activeProcessInstanceCounterColumnFamily.get(activeProcessInstanceCounterKey);

    if (currentValue == null) {
      activeProcessInstanceCounterValue.wrapLong(0);
      activeProcessInstanceCounterColumnFamily.insert(
          activeProcessInstanceCounterKey, activeProcessInstanceCounterValue);
    }
  }

  private static class KeyWithTenantId<T extends DbKey> extends DbCompositeKey<T, DbString> {

    public KeyWithTenantId(final T key, final DbString tenantId) {
      super(key, tenantId);
    }
  }

  private static class BusinessIdAndProcessDefinitionId extends DbCompositeKey<DbString, DbString> {
    public BusinessIdAndProcessDefinitionId(
        final DbString businessId, final DbString processDefinitionId) {
      super(businessId, processDefinitionId);
    }
  }

  private static class BusinessIdIndexKey
      extends KeyWithTenantId<BusinessIdAndProcessDefinitionId> {
    public BusinessIdIndexKey(
        final DbString businessId, final DbString processDefinitionId, final DbString tenantId) {
      super(new BusinessIdAndProcessDefinitionId(businessId, processDefinitionId), tenantId);
    }
  }

  private static class ProcessInstanceByBusinessIdIndexKey
      extends DbCompositeKey<KeyWithTenantId<BusinessIdAndProcessDefinitionId>, DbLong> {

    public ProcessInstanceByBusinessIdIndexKey(
        final BusinessIdIndexKey businessIdIndexKey, final DbLong processInstanceKey) {
      super(businessIdIndexKey, processInstanceKey);
    }

    public long processInstanceKey() {
      return second().getValue();
    }
  }
}
