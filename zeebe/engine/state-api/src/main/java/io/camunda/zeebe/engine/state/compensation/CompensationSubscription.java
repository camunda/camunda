/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.compensation;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class CompensationSubscription extends UnpackedObject implements DbValue {

  private final ObjectProperty<CompensationSubscriptionRecord> recordProp =
      new ObjectProperty<>("compensationSubscriptionRecord", new CompensationSubscriptionRecord());

  private final LongProperty keyProp = new LongProperty("key");

  public CompensationSubscription() {
    super(2);
    declareProperty(recordProp).declareProperty(keyProp);
  }

  public CompensationSubscription copy() {
    final var copy = new CompensationSubscription();
    copy.setKey(getKey());

    final CompensationSubscriptionRecord original = getRecord();
    copy.getRecord().wrap(original);
    copy.getRecord().setVariables(BufferUtil.cloneBuffer(original.getVariablesBuffer()));

    return copy;
  }

  public CompensationSubscriptionRecord getRecord() {
    return recordProp.getValue();
  }

  public void setRecord(final CompensationSubscriptionRecord record) {
    recordProp.getValue().wrap(record);
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public CompensationSubscription setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }
}
