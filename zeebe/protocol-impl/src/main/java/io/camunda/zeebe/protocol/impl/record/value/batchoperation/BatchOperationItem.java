/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;

public final class BatchOperationItem extends ObjectValue implements BatchOperationItemValue {

  private final LongProperty itemKeyProperty = new LongProperty("itemKey", -1);
  private final LongProperty processInstanceKeyProperty =
      new LongProperty("processInstanceKey", -1);
  private final LongProperty rootProcessInstanceKeyProperty =
      new LongProperty("rootProcessInstanceKey", -1);
  private final IntegerProperty ordinalKeyProperty = new IntegerProperty("ordinalKey", 0);

  public BatchOperationItem() {
    super(4);
    declareProperty(itemKeyProperty)
        .declareProperty(processInstanceKeyProperty)
        .declareProperty(rootProcessInstanceKeyProperty)
        .declareProperty(ordinalKeyProperty);
  }

  public BatchOperationItem(
      final long itemKey, final long processInstanceKey, final long rootProcessInstanceKey) {
    this();
    setItemKey(itemKey);
    setProcessInstanceKey(processInstanceKey);
    setRootProcessInstanceKey(rootProcessInstanceKey);
  }

  @Override
  public long getItemKey() {
    return itemKeyProperty.getValue();
  }

  public BatchOperationItem setItemKey(final long itemKey) {
    itemKeyProperty.setValue(itemKey);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public BatchOperationItem setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKeyProperty.getValue();
  }

  public BatchOperationItem setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    rootProcessInstanceKeyProperty.setValue(rootProcessInstanceKey);
    return this;
  }

  @Override
  public int getOrdinalKey() {
    return ordinalKeyProperty.getValue();
  }

  public BatchOperationItem setOrdinalKey(final int ordinalKey) {
    ordinalKeyProperty.setValue(ordinalKey);
    return this;
  }

  public BatchOperationItem wrap(final BatchOperationItemValue value) {
    setItemKey(value.getItemKey());
    setProcessInstanceKey(value.getProcessInstanceKey());
    setRootProcessInstanceKey(value.getRootProcessInstanceKey());
    setOrdinalKey(value.getOrdinalKey());
    return this;
  }
}
