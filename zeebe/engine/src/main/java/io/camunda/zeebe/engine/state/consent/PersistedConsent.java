/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.consent;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;

public final class PersistedConsent extends UnpackedObject implements DbValue {

  private final ObjectProperty<ConsentRecord> consentProp =
      new ObjectProperty<>("consent", new ConsentRecord());

  public PersistedConsent() {
    super(1);
    declareProperty(consentProp);
  }

  public PersistedConsent setRecord(final ConsentRecord record) {
    consentProp.getValue().copyFrom(record);
    return this;
  }

  public ConsentRecord getRecord() {
    return consentProp.getValue();
  }
}
