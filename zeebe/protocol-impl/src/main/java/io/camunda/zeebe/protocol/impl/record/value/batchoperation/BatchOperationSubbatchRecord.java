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
import io.camunda.zeebe.protocol.record.value.BatchOperationSubbatchRecordValue;
import java.util.List;
import java.util.stream.Collectors;

public final class BatchOperationSubbatchRecord extends UnifiedRecordValue
    implements BatchOperationSubbatchRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_SUBBATCH_KEY = "subbatch";
  public static final String PROP_KEY_LIST = "keys";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY);
  private final LongProperty subbatchKeyProp = new LongProperty(PROP_SUBBATCH_KEY);
  private final ArrayProperty<LongValue> keysProp =
      new ArrayProperty<>(PROP_KEY_LIST, LongValue::new);

  public BatchOperationSubbatchRecord() {
    super(4);
    declareProperty(batchOperationKeyProp)
        .declareProperty(subbatchKeyProp)
        .declareProperty(keysProp);
  }

  @Override
  public Long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public BatchOperationSubbatchRecord setBatchOperationKey(final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  @Override
  public List<Long> getKeys() {
    return keysProp.stream().map(LongValue::getValue).collect(Collectors.toList());
  }

  public BatchOperationSubbatchRecord setKeys(final List<Long> keys) {
    keysProp.reset();
    keys.forEach(key -> keysProp.add().setValue(key));
    return this;
  }

  @Override
  public Long getSubbatchKey() {
    return subbatchKeyProp.getValue();
  }

  public BatchOperationSubbatchRecord setSubbatchKey(final Long key) {
    subbatchKeyProp.setValue(key);
    return this;
  }

  public void wrap(final BatchOperationSubbatchRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setKeys(record.getKeys());
    setSubbatchKey(record.getSubbatchKey());
  }
}
