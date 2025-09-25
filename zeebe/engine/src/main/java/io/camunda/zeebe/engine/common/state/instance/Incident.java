/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;

public class Incident extends UnpackedObject implements DbValue {
  private final ObjectProperty<IncidentRecord> recordProp =
      new ObjectProperty<>("incidentRecord", new IncidentRecord());

  public Incident() {
    super(1);
    declareProperty(recordProp);
  }

  public IncidentRecord getRecord() {
    return recordProp.getValue();
  }

  public void setRecord(final IncidentRecord record) {
    recordProp.getValue().wrap(record);
  }
}
