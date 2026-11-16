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
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecordV1;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class CompensationSubscriptionV1 extends UnpackedObject implements DbValue {

  private final ObjectProperty<CompensationSubscriptionRecordV1> recordProp =
      new ObjectProperty<>(
          "compensationSubscriptionRecord", new CompensationSubscriptionRecordV1());

  private final LongProperty keyProp = new LongProperty("key");

  public CompensationSubscriptionV1() {
    super(2);
    declareProperty(recordProp).declareProperty(keyProp);
  }

  public CompensationSubscriptionV1 copy() {
    final var copy = new CompensationSubscriptionV1();
    copy.setKey(getKey());

    final CompensationSubscriptionRecordV1 original = getRecord();
    copy.getRecord().wrap(original);
    copy.getRecord().setVariables(BufferUtil.cloneBuffer(original.getVariablesBuffer()));

    return copy;
  }

  public CompensationSubscriptionRecordV1 getRecord() {
    return recordProp.getValue();
  }

  public void setRecord(final CompensationSubscriptionRecordV1 record) {
    recordProp.getValue().wrap(record);
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public CompensationSubscriptionV1 setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }
}
