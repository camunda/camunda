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
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;

public class VariableDocumentState extends UnpackedObject implements DbValue {

  private final LongProperty variableDocumentKeyProp = new LongProperty("variableDocumentKey", -1);
  private final ObjectProperty<VariableDocumentRecord> recordProp =
      new ObjectProperty<>("variableDocumentRecord", new VariableDocumentRecord());

  public VariableDocumentState() {
    super(2);
    declareProperty(variableDocumentKeyProp).declareProperty(recordProp);
  }

  public long getKey() {
    return variableDocumentKeyProp.getValue();
  }

  public VariableDocumentState setKey(final long variableDocumentKey) {
    variableDocumentKeyProp.setValue(variableDocumentKey);
    return this;
  }

  public VariableDocumentRecord getRecord() {
    return recordProp.getValue();
  }

  public VariableDocumentState setRecord(final VariableDocumentRecord record) {
    recordProp.getValue().wrap(record);
    return this;
  }
}
