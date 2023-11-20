/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;

public class UserTaskRecordValue extends UnpackedObject implements DbValue {

  private final ObjectProperty<UserTaskRecord> recordProp =
      new ObjectProperty<>("userTaskRecord", new UserTaskRecord());

  public UserTaskRecordValue() {
    declareProperty(recordProp);
  }

  public UserTaskRecord getRecord() {
    return recordProp.getValue();
  }

  public void setRecordWithoutVariables(final UserTaskRecord record) {
    recordProp.getValue().wrapWithoutVariables(record);
  }
}
