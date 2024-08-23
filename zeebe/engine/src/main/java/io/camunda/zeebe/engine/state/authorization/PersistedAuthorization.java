/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;

public class PersistedAuthorization extends UnpackedObject implements DbValue {
  private final ObjectProperty<AuthorizationRecord> authorizationRecord =
      new ObjectProperty<>("authorizationRecord", new AuthorizationRecord());

  public PersistedAuthorization() {
    super(1);
    declareProperty(authorizationRecord);
  }

  public AuthorizationRecord getAuthorization() {
    return authorizationRecord.getValue();
  }

  public void setAuthorization(final AuthorizationRecord record) {
    authorizationRecord.getValue().wrap(record);
  }
}
