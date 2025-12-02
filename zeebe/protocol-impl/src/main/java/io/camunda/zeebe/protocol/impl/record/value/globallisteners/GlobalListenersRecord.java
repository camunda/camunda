/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.globallisteners;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenersRecordValue;
import java.util.List;
import java.util.stream.Collectors;

public final class GlobalListenersRecord extends UnifiedRecordValue
    implements GlobalListenersRecordValue {

  private final LongProperty listenersConfigKey = new LongProperty("listenersConfigKey", -1L);
  private final ArrayProperty<GlobalListenerRecord> taskListenersProp =
      new ArrayProperty<>("taskListeners", GlobalListenerRecord::new);

  public GlobalListenersRecord() {
    super(2);
    declareProperty(listenersConfigKey).declareProperty(taskListenersProp);
  }

  @Override
  public long getListenersConfigKey() {
    return listenersConfigKey.getValue();
  }

  public GlobalListenersRecord setListenersConfigKey(final long listenersConfigKey) {
    this.listenersConfigKey.setValue(listenersConfigKey);
    return this;
  }

  @Override
  public List<GlobalListenerRecordValue> getTaskListeners() {
    return taskListenersProp.stream().collect(Collectors.toList());
  }

  public GlobalListenersRecord addTaskListener(final GlobalListenerRecord listener) {
    taskListenersProp.add().copyFrom(listener);
    return this;
  }
}
