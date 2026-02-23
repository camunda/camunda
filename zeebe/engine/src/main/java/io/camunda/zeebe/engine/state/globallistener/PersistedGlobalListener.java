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
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;

public final class PersistedGlobalListener extends UnpackedObject implements DbValue {

  private final ObjectProperty<GlobalListenerRecord> globalListener =
      new ObjectProperty<>("globalListener", new GlobalListenerRecord());

  public PersistedGlobalListener() {
    super(1);
    declareProperty(globalListener);
  }

  public GlobalListenerRecord getGlobalListener() {
    return globalListener.getValue();
  }

  public void setGlobalListener(final GlobalListenerRecord record) {
    globalListener.getValue().copyFrom(record);
  }
}
