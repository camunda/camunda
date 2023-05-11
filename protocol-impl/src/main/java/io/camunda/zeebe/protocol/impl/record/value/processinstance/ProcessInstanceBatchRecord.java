/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBatchRecordValue;

public final class ProcessInstanceBatchRecord extends UnifiedRecordValue
    implements ProcessInstanceBatchRecordValue {

  private final LongProperty processInstanceKeyProperty = new LongProperty("processInstanceKey");
  private final LongProperty batchElementInstanceKeyProperty =
      new LongProperty("batchElementInstanceKey");

  /**
   * The index is used to keep track of the position in the batch. When the index is -1, there won't
   * be another batch.
   *
   * <p>Depending on the Intent the index is used differently:
   *
   * <ul>
   *   <li>TERMINATE - The index is the element instance key of the first child instance of the next
   *       batch.
   *   <li>ACTIVATE - The index is a counter, indicating how many more child instances need to be
   *       activated.
   * </ul>
   */
  private final LongProperty indexProperty = new LongProperty("index", -1L);

  public ProcessInstanceBatchRecord() {
    declareProperty(processInstanceKeyProperty)
        .declareProperty(batchElementInstanceKeyProperty)
        .declareProperty(indexProperty);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public ProcessInstanceBatchRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getBatchElementInstanceKey() {
    return batchElementInstanceKeyProperty.getValue();
  }

  public ProcessInstanceBatchRecord setBatchElementInstanceKey(final long batchElementInstanceKey) {
    batchElementInstanceKeyProperty.setValue(batchElementInstanceKey);
    return this;
  }

  @Override
  public long getIndex() {
    return indexProperty.getValue();
  }

  public ProcessInstanceBatchRecord setIndex(final long index) {
    indexProperty.setValue(index);
    return this;
  }
}
