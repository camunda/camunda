/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.globallistener;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import java.util.List;
import java.util.stream.Collectors;

public final class GlobalListenerBatchRecord extends UnifiedRecordValue
    implements GlobalListenerBatchRecordValue {

  private final LongProperty listenersConfigKey = new LongProperty("listenersConfigKey", -1L);
  private final ArrayProperty<GlobalListenerRecord> taskListenersProp =
      new ArrayProperty<>("taskListeners", GlobalListenerRecord::new);

  public GlobalListenerBatchRecord() {
    super(2);
    declareProperty(listenersConfigKey).declareProperty(taskListenersProp);
  }

  @Override
  public long getListenersConfigKey() {
    return listenersConfigKey.getValue();
  }

  public GlobalListenerBatchRecord setListenersConfigKey(final long listenersConfigKey) {
    this.listenersConfigKey.setValue(listenersConfigKey);
    return this;
  }

  @Override
  public List<GlobalListenerRecordValue> getTaskListeners() {
    return taskListenersProp.stream().collect(Collectors.toList());
  }

  public GlobalListenerBatchRecord addTaskListener(final GlobalListenerRecord listener) {
    taskListenersProp.add().copyFrom(listener);
    return this;
  }
}
