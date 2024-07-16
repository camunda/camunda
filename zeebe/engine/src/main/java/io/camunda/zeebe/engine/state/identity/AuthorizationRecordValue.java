/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.identity;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.identity.AuthorizationRecord;

public class AuthorizationRecordValue extends UnpackedObject implements DbValue {
  private final ObjectProperty<AuthorizationRecord> recordProp =
      new ObjectProperty<>("authorizationRecord", new AuthorizationRecord());

  public AuthorizationRecordValue() {
    super(1);
    declareProperty(recordProp);
  }

  public AuthorizationRecord getRecord() {
    return recordProp.getValue();
  }

  public void setRecord(final AuthorizationRecord record) {
    recordProp.getValue().wrap(record);
  }
}
