/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ObjectProperty;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;

public class Incident extends UnpackedObject implements DbValue {
  private final ObjectProperty<IncidentRecord> recordProp =
      new ObjectProperty<>("incidentRecord", new IncidentRecord());

  public Incident() {
    declareProperty(recordProp);
  }

  public IncidentRecord getRecord() {
    return recordProp.getValue();
  }

  public void setRecord(final IncidentRecord record) {
    recordProp.getValue().wrap(record);
  }
}
