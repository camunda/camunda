/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.globallistener;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;

public final class PersistedGlobalListenersConfig extends UnpackedObject implements DbValue {

  private final ObjectProperty<GlobalListenerBatchRecord> globalListeners =
      new ObjectProperty<>("globalListeners", new GlobalListenerBatchRecord());

  public PersistedGlobalListenersConfig() {
    super(1);
    declareProperty(globalListeners);
  }

  public GlobalListenerBatchRecord getGlobalListeners() {
    return globalListeners.getValue();
  }

  public void setGlobalListeners(final GlobalListenerBatchRecord record) {
    globalListeners.getValue().copyFrom(record);
  }
}
