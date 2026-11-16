/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.globallistener;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class GlobalListenerBatchRecordV1 extends UnifiedRecordValue
    implements GlobalListenerBatchRecordValue {

  private final LongProperty globalListenerBatchKey =
      new LongProperty("globalListenerBatchKey", -1L);
  private final ArrayProperty<GlobalListenerRecordV1> listenersProp =
      new ArrayProperty<>("listeners", GlobalListenerRecordV1::new);

  // Metadata to apply state mutations on command distribution
  private final ArrayProperty<LongValue> createdListenerKeysProp =
      new ArrayProperty<>("createdListenerKeys", LongValue::new);
  private final ArrayProperty<LongValue> updatedListenerKeysProp =
      new ArrayProperty<>("updatedListenerKeys", LongValue::new);
  private final ArrayProperty<LongValue> deletedListenerKeysProp =
      new ArrayProperty<>("deletedListenerKeys", LongValue::new);

  public GlobalListenerBatchRecordV1() {
    super(5);
    declareProperty(globalListenerBatchKey)
        .declareProperty(listenersProp)
        .declareProperty(createdListenerKeysProp)
        .declareProperty(updatedListenerKeysProp)
        .declareProperty(deletedListenerKeysProp);
  }

  @Override
  public long getGlobalListenerBatchKey() {
    return globalListenerBatchKey.getValue();
  }

  public GlobalListenerBatchRecordV1 setGlobalListenerBatchKey(final long globalListenerBatchKey) {
    this.globalListenerBatchKey.setValue(globalListenerBatchKey);
    return this;
  }

  @Override
  public List<GlobalListenerRecordValue> getListeners() {
    return listenersProp.stream().collect(Collectors.toList());
  }

  @Override
  public Set<Long> getCreatedListenerKeys() {
    return createdListenerKeysProp.stream().map(LongValue::getValue).collect(Collectors.toSet());
  }

  @Override
  public Set<Long> getUpdatedListenerKeys() {
    return updatedListenerKeysProp.stream().map(LongValue::getValue).collect(Collectors.toSet());
  }

  @Override
  public Set<Long> getDeletedListenerKeys() {
    return deletedListenerKeysProp.stream().map(LongValue::getValue).collect(Collectors.toSet());
  }

  @JsonIgnore
  public List<GlobalListenerRecordV1> getTaskListeners() {
    return listenersProp.stream()
        .filter(listener -> listener.getListenerType() == GlobalListenerType.USER_TASK)
        .toList();
  }

  /**
   * Check if two global listener batches lead to equivalent configurations, ignoring the keys of
   * the batch and the listeners.
   */
  public boolean isSameConfiguration(final GlobalListenerBatchRecordV1 other) {
    final UnaryOperator<GlobalListenerRecordV1> makeComparable =
        listener -> {
          final GlobalListenerRecordV1 copy = new GlobalListenerRecordV1();
          copy.copyFrom(listener);
          copy.setGlobalListenerKey(-1L); // ignore keys when comparing listeners
          return copy;
        };
    final Set<GlobalListenerRecordV1> thisListeners =
        listenersProp.stream().map(makeComparable).collect(Collectors.toSet());
    final Set<GlobalListenerRecordV1> otherListeners =
        other.listenersProp.stream().map(makeComparable).collect(Collectors.toSet());
    return thisListeners.equals(otherListeners);
  }

  public GlobalListenerBatchRecordV1 addListener(final GlobalListenerRecordV1 listener) {
    listenersProp.add().copyFrom(listener);
    return this;
  }

  public GlobalListenerBatchRecordV1 addCreatedListener(final GlobalListenerRecordV1 listener) {
    createdListenerKeysProp.add().setValue(listener.getGlobalListenerKey());
    return this;
  }

  public GlobalListenerBatchRecordV1 addUpdatedListener(final GlobalListenerRecordV1 listener) {
    updatedListenerKeysProp.add().setValue(listener.getGlobalListenerKey());
    return this;
  }

  public GlobalListenerBatchRecordV1 addDeletedListener(final GlobalListenerRecordV1 listener) {
    deletedListenerKeysProp.add().setValue(listener.getGlobalListenerKey());
    return this;
  }
}
