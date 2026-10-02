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

  /**
   * The index of the position in the suspension batch. When the index is {@code -1}, there are no
   * more children to visit.
   *
   * <p>This is the element instance key of the next child to visit for most intents, or the key the
   * scope for which traversal was completed for {@code COMPLETE_SUSPENDING_ELEMENT_INSTANCE}.
   */
  private final LongProperty indexKeyProperty = new LongProperty("indexKey", -1L);

  /**
   * The parent key used to find its children and siblings. {@code -1} identifies the traversal root
   * without a parent.
   */
  private final LongProperty parentKeyProperty = new LongProperty("parentKey", -1L);

  private final IntegerProperty storageOrdinalProperty = new IntegerProperty("storageOrdinal", 0);

  public SuspensionBatchRecord() {
    super(5);
    declareProperty(processInstanceKeyProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(indexKeyProperty)
        .declareProperty(parentKeyProperty)
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
  public long getIndexKey() {
    return indexKeyProperty.getValue();
  }

  public SuspensionBatchRecord setIndexKey(final long indexKey) {
    indexKeyProperty.setValue(indexKey);
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
  public int getStorageOrdinal() {
    return storageOrdinalProperty.getValue();
  }

  public SuspensionBatchRecord setStorageOrdinal(final int storageOrdinal) {
    storageOrdinalProperty.setValue(storageOrdinal);
    return this;
  }
}
