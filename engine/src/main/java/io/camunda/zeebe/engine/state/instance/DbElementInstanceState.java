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
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

public final class DbElementInstanceState implements MutableElementInstanceState {

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

  private final MutableVariableState variableState;

  public DbElementInstanceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final MutableVariableState variableState) {

    this.variableState = variableState;

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

    final ElementInstance instance;
    if (parent == null) {
      instance = new ElementInstance(key, state, value);
    } else {
      instance = new ElementInstance(key, parent, state, value);
      updateInstance(parent);
    }
    createInstance(instance);

    return instance;
  }

  @Override
  public void removeInstance(final long key) {
    elementInstanceKey.wrapLong(key);
    final var instance = elementInstanceColumnFamily.get(elementInstanceKey);
    if (instance == null) {
      return;
    }
    final long parent = instance.getParentKey();
    parentKey.inner().wrapLong(parent);
    parentChildColumnFamily.deleteIfExists(parentChildKey);
    elementInstanceColumnFamily.deleteExisting(elementInstanceKey);
    variableState.removeScope(key);
    awaitProcessInstanceResultMetadataColumnFamily.deleteIfExists(elementInstanceKey);
    removeNumberOfTakenSequenceFlows(key);

    if (parent > 0) {
      elementInstanceKey.wrapLong(parent);
      final var parentInstance = elementInstanceColumnFamily.get(elementInstanceKey);
      if (parentInstance == null) {
        final var errorMsg =
            "Expected to find parent instance for element instance with key %d, but none was found.";
        throw new IllegalStateException(String.format(errorMsg, parent));
      }
      parentInstance.decrementChildCount();
      updateInstance(parentInstance);
    }
  }

  @Override
  public void createInstance(final ElementInstance instance) {
    elementInstanceKey.wrapLong(instance.getKey());
    parentKey.inner().wrapLong(instance.getParentKey());

    elementInstanceColumnFamily.insert(elementInstanceKey, instance);
    parentChildColumnFamily.insert(parentChildKey, DbNil.INSTANCE);
    variableState.createScope(elementInstanceKey.getValue(), parentKey.inner().getValue());
  }

  @Override
  public void updateInstance(final ElementInstance scopeInstance) {
    elementInstanceKey.wrapLong(scopeInstance.getKey());
    parentKey.inner().wrapLong(scopeInstance.getParentKey());
    elementInstanceColumnFamily.update(elementInstanceKey, scopeInstance);
  }

  @Override
  public void updateInstance(final long key, final Consumer<ElementInstance> modifier) {
    elementInstanceKey.wrapLong(key);
    final var scopeInstance = elementInstanceColumnFamily.get(elementInstanceKey);
    modifier.accept(scopeInstance);
    updateInstance(scopeInstance);
  }

  @Override
  public void setAwaitResultRequestMetadata(
      final long processInstanceKey, final AwaitProcessInstanceResultMetadata metadata) {
    elementInstanceKey.wrapLong(processInstanceKey);
    awaitProcessInstanceResultMetadataColumnFamily.insert(elementInstanceKey, metadata);
  }

  @Override
  public void incrementNumberOfTakenSequenceFlows(
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
  public void decrementNumberOfTakenSequenceFlows(
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
  public ElementInstance getInstance(final long key) {
    elementInstanceKey.wrapLong(key);
    final ElementInstance elementInstance = elementInstanceColumnFamily.get(elementInstanceKey);
    return copyElementInstance(elementInstance);
  }

  @Override
  public List<ElementInstance> getChildren(final long parentKey) {
    final List<ElementInstance> children = new ArrayList<>();
    final ElementInstance parentInstance = getInstance(parentKey);
    if (parentInstance != null) {
      this.parentKey.inner().wrapLong(parentKey);

      parentChildColumnFamily.whileEqualPrefix(
          this.parentKey,
          (key, value) -> {
            final DbLong childKey = key.second().inner();
            final ElementInstance childInstance = getInstance(childKey.getValue());

            final ElementInstance copiedElementInstance = copyElementInstance(childInstance);
            children.add(copiedElementInstance);
          });
    }
    return children;
  }

  @Override
  public AwaitProcessInstanceResultMetadata getAwaitResultRequestMetadata(
      final long processInstanceKey) {
    elementInstanceKey.wrapLong(processInstanceKey);
    return awaitProcessInstanceResultMetadataColumnFamily.get(elementInstanceKey);
  }

  @Override
  public int getNumberOfTakenSequenceFlows(
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
          numberOfTakenSequenceFlowsColumnFamily.deleteExisting(key);
        });
  }
}
