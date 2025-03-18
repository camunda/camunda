/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import java.util.Set;
import java.util.stream.Collectors;

public final class BatchOperationExecutionRecord extends UnifiedRecordValue
    implements BatchOperationExecutionRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_ENTITY_KEY_LIST = "entitykeys";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY);
  private final ArrayProperty<LongValue> entityKeysProp =
      new ArrayProperty<>(PROP_ENTITY_KEY_LIST, LongValue::new);

  public BatchOperationExecutionRecord() {
    super(2);
    declareProperty(batchOperationKeyProp).declareProperty(entityKeysProp);
  }

  @Override
  public Long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public BatchOperationExecutionRecord setBatchOperationKey(final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  @Override
  public Set<Long> getEntityKeys() {
    return entityKeysProp.stream().map(LongValue::getValue).collect(Collectors.toSet());
  }

  public BatchOperationExecutionRecord setKeys(final Set<Long> keys) {
    entityKeysProp.reset();
    keys.forEach(key -> entityKeysProp.add().setValue(key));
    return this;
  }

  public BatchOperationExecutionRecord wrap(final BatchOperationExecutionRecord record) {
    setKeys(record.getEntityKeys());
    return this;
  }
}
