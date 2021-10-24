/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

public final class DbElementInstanceState implements MutableElementInstanceState {

  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> parentChildColumnFamily;
  private final DbCompositeKey<DbLong, DbLong> parentChildKey;
  private final DbLong parentKey;

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

  private final MutableVariableState variableState;

  public DbElementInstanceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final MutableVariableState variableState) {

    this.variableState = variableState;

    elementInstanceKey = new DbLong();
    parentKey = new DbLong();
    parentChildKey = new DbCompositeKey<>(parentKey, elementInstanceKey);
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
  }

  @Override
  public synchronized ElementInstance newInstance(
      final long key, final ProcessInstanceRecord value, final ProcessInstanceIntent state) {
    return newInstance(null, key, value, state);
  }

  @Override
  public synchronized ElementInstance newInstance(
      final ElementInstance parent,
      final long key,
      final ProcessInstanceRecord value,
      final ProcessInstanceIntent state) {

    final ElementInstance instance;
    if (parent == null) {
      instance = new ElementInstance(key, state, value);
    } else {
      instance = new ElementInstance(key, parent, state, value);
      updateInstance(parent);
    }
    updateInstance(instance);

    return instance;
  }

  @Override
  public synchronized void removeInstance(final long key) {
    final ElementInstance instance = getInstance(key);

    if (instance != null) {
      elementInstanceKey.wrapLong(key);
      parentKey.wrapLong(instance.getParentKey());

      parentChildColumnFamily.delete(parentChildKey);
      elementInstanceColumnFamily.delete(elementInstanceKey);

      variableState.removeScope(key);

      awaitProcessInstanceResultMetadataColumnFamily.delete(elementInstanceKey);
      removeNumberOfTakenSequenceFlows(key);

      final long parentKey = instance.getParentKey();
      if (parentKey > 0) {
        final ElementInstance parentInstance = getInstance(parentKey);
        if (parentInstance == null) {
          final var errorMsg =
              "Expected to find parent instance for element instance with key %d, but none was found.";
          throw new IllegalStateException(String.format(errorMsg, parentKey));
        }
        parentInstance.decrementChildCount();
        updateInstance(parentInstance);
      }
    }
  }

  @Override
  public synchronized void updateInstance(final ElementInstance scopeInstance) {
    writeElementInstance(scopeInstance);
  }

  @Override
  public synchronized void updateInstance(
      final long key, final Consumer<ElementInstance> modifier) {
    final var scopeInstance = getInstance(key);
    modifier.accept(scopeInstance);
    updateInstance(scopeInstance);
  }

  @Override
  public synchronized void setAwaitResultRequestMetadata(
      final long processInstanceKey, final AwaitProcessInstanceResultMetadata metadata) {
    elementInstanceKey.wrapLong(processInstanceKey);
    awaitProcessInstanceResultMetadataColumnFamily.put(elementInstanceKey, metadata);
  }

  @Override
  public synchronized void incrementNumberOfTakenSequenceFlows(
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

    numberOfTakenSequenceFlowsColumnFamily.put(
        numberOfTakenSequenceFlowsKey, numberOfTakenSequenceFlows);
  }

  @Override
  public synchronized void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    this.flowScopeKey.wrapLong(flowScopeKey);
    this.gatewayElementId.wrapBuffer(gatewayElementId);

    numberOfTakenSequenceFlowsColumnFamily.whileEqualPrefix(
        flowScopeKeyAndElementId,
        (key, number) -> {
          final var newValue = number.getValue() - 1;
          if (newValue > 0) {
            numberOfTakenSequenceFlows.wrapInt(newValue);
            numberOfTakenSequenceFlowsColumnFamily.put(key, numberOfTakenSequenceFlows);
          } else {
            numberOfTakenSequenceFlowsColumnFamily.delete(key);
          }
        });
  }

  private void writeElementInstance(final ElementInstance instance) {
    elementInstanceKey.wrapLong(instance.getKey());
    parentKey.wrapLong(instance.getParentKey());

    elementInstanceColumnFamily.put(elementInstanceKey, instance);
    parentChildColumnFamily.put(parentChildKey, DbNil.INSTANCE);
    variableState.createScope(elementInstanceKey.getValue(), parentKey.getValue());
  }

  @Override
  public synchronized ElementInstance getInstance(final long key) {
    elementInstanceKey.wrapLong(key);
    final ElementInstance elementInstance = elementInstanceColumnFamily.get(elementInstanceKey);
    return copyElementInstance(elementInstance);
  }

  @Override
  public synchronized List<ElementInstance> getChildren(final long parentKey) {
    final List<ElementInstance> children = new ArrayList<>();
    final ElementInstance parentInstance = getInstance(parentKey);
    if (parentInstance != null) {
      this.parentKey.wrapLong(parentKey);

      parentChildColumnFamily.whileEqualPrefix(
          this.parentKey,
          (key, value) -> {
            final DbLong childKey = key.getSecond();
            final ElementInstance childInstance = getInstance(childKey.getValue());

            final ElementInstance copiedElementInstance = copyElementInstance(childInstance);
            children.add(copiedElementInstance);
          });
    }
    return children;
  }

  @Override
  public synchronized AwaitProcessInstanceResultMetadata getAwaitResultRequestMetadata(
      final long processInstanceKey) {
    elementInstanceKey.wrapLong(processInstanceKey);
    return awaitProcessInstanceResultMetadataColumnFamily.get(elementInstanceKey);
  }

  @Override
  public synchronized int getNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    this.flowScopeKey.wrapLong(flowScopeKey);
    this.gatewayElementId.wrapBuffer(gatewayElementId);

    final var count = new MutableInteger(0);
    numberOfTakenSequenceFlowsColumnFamily.whileEqualPrefix(
        flowScopeKeyAndElementId,
        (key, number) -> {
          count.increment();
        });

    return count.get();
  }

  private ElementInstance copyElementInstance(final ElementInstance elementInstance) {
    if (elementInstance != null) {
      final byte[] bytes = new byte[elementInstance.getLength()];
      final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

      elementInstance.write(buffer, 0);
      final ElementInstance copiedElementInstance = new ElementInstance();
      copiedElementInstance.wrap(buffer, 0, elementInstance.getLength());
      return copiedElementInstance;
    }
    return null;
  }

  private void removeNumberOfTakenSequenceFlows(final long flowScopeKey) {
    this.flowScopeKey.wrapLong(flowScopeKey);

    numberOfTakenSequenceFlowsColumnFamily.whileEqualPrefix(
        this.flowScopeKey,
        (key, number) -> {
          numberOfTakenSequenceFlowsColumnFamily.delete(key);
        });
  }

  @FunctionalInterface
  public interface RecordVisitor {

    void visitRecord(IndexedRecord indexedRecord);
  }
}
