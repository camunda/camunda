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
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.List;
import java.util.stream.Collectors;

public final class GlobalListenerBatchRecord extends UnifiedRecordValue
    implements GlobalListenerBatchRecordValue {

  private final LongProperty globalListenerBatchKey =
      new LongProperty("globalListenerBatchKey", -1L);
  private final ArrayProperty<GlobalListenerRecord> listenersProp =
      new ArrayProperty<>("listeners", GlobalListenerRecord::new);

  public GlobalListenerBatchRecord() {
    super(2);
    declareProperty(globalListenerBatchKey).declareProperty(listenersProp);
  }

  @Override
  public long getGlobalListenerBatchKey() {
    return globalListenerBatchKey.getValue();
  }

  public GlobalListenerBatchRecord setGlobalListenerBatchKey(final long globalListenerBatchKey) {
    this.globalListenerBatchKey.setValue(globalListenerBatchKey);
    return this;
  }

  @Override
  public List<GlobalListenerRecordValue> getListeners() {
    return listenersProp.stream().collect(Collectors.toList());
  }

  public GlobalListenerBatchRecord addListener(final GlobalListenerRecord listener) {
    listenersProp.add().copyFrom(listener);
    return this;
  }

  @JsonIgnore
  public List<GlobalListenerRecord> getTaskListeners() {
    return listenersProp.stream()
        .filter(listener -> listener.getListenerType() == GlobalListenerType.USER_TASK)
        .toList();
  }
}
