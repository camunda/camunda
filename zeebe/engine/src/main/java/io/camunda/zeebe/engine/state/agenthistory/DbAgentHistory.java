/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agenthistory;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;

public final class DbAgentHistory extends UnpackedObject implements DbValue {

  private final ObjectProperty<AgentHistoryRecord> recordProp =
      new ObjectProperty<>("agentHistoryRecord", new AgentHistoryRecord());

  public DbAgentHistory() {
    super(1);
    declareProperty(recordProp);
  }

  public AgentHistoryRecord getRecord() {
    return recordProp.getValue();
  }

  public void setRecord(final AgentHistoryRecord record) {
    recordProp.getValue().copyFrom(record);
  }
}
