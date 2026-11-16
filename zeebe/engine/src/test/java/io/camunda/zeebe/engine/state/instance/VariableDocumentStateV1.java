/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecordV1;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class VariableDocumentStateV1 extends UnpackedObject implements DbValue {

  private final LongProperty variableDocumentKeyProp = new LongProperty("variableDocumentKey", -1);
  private final ObjectProperty<VariableDocumentRecordV1> recordProp =
      new ObjectProperty<>("variableDocumentRecord", new VariableDocumentRecordV1());

  public VariableDocumentStateV1() {
    super(2);
    declareProperty(variableDocumentKeyProp).declareProperty(recordProp);
  }

  public long getKey() {
    return variableDocumentKeyProp.getValue();
  }

  public VariableDocumentStateV1 setKey(final long variableDocumentKey) {
    variableDocumentKeyProp.setValue(variableDocumentKey);
    return this;
  }

  public VariableDocumentRecordV1 getRecord() {
    return recordProp.getValue();
  }

  public VariableDocumentStateV1 setRecord(final VariableDocumentRecordV1 record) {
    recordProp.getValue().wrap(record);
    return this;
  }
}
