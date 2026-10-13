/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.SuspensionBatchRecordValue;

public final class SuspensionBatchRecord extends UnifiedRecordValue
    implements SuspensionBatchRecordValue {

  private final LongProperty processInstanceKeyProperty =
      new LongProperty("processInstanceKey", -1L);
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty("processDefinitionKey", -1L);

  /** The element instance to suspend. */
  private final LongProperty elementInstanceKeyProperty =
      new LongProperty("elementInstanceKey", -1L);

  /**
   * The parent of {@code elementInstanceKey}, or {@code -1} for the process instance; used to
   * continue the walk if the element instance is gone.
   */
  private final LongProperty parentKeyProperty = new LongProperty("parentKey", -1L);

  /**
   * The child of {@code elementInstanceKey} that was suspended last, so the walk continues after
   * it; {@code -1} to start at the first child.
   */
  private final LongProperty indexProperty = new LongProperty("index", -1L);

  private final IntegerProperty storageOrdinalProperty = new IntegerProperty("storageOrdinal", 0);

  public SuspensionBatchRecord() {
    super(6);
    declareProperty(processInstanceKeyProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(elementInstanceKeyProperty)
        .declareProperty(parentKeyProperty)
        .declareProperty(indexProperty)
        .declareProperty(storageOrdinalProperty);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public SuspensionBatchRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProperty.getValue();
  }

  public SuspensionBatchRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProperty.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProperty.getValue();
  }

  public SuspensionBatchRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProperty.setValue(elementInstanceKey);
    return this;
  }

  @Override
  public long getParentKey() {
    return parentKeyProperty.getValue();
  }

  public SuspensionBatchRecord setParentKey(final long parentKey) {
    parentKeyProperty.setValue(parentKey);
    return this;
  }

  @Override
  public long getIndex() {
    return indexProperty.getValue();
  }

  public SuspensionBatchRecord setIndex(final long index) {
    indexProperty.setValue(index);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinalProperty.getValue();
  }

  public SuspensionBatchRecord setStorageOrdinal(final int storageOrdinal) {
    storageOrdinalProperty.setValue(storageOrdinal);
    return this;
  }
}
