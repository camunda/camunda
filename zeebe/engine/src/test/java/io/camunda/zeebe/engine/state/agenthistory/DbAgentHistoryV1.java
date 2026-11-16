/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agenthistory;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.RecordObjectValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class DbAgentHistoryV1 extends UnpackedObject implements DbValue {

  private final ObjectProperty<RecordObjectValue<AgentHistoryRecord>> recordProp =
      new ObjectProperty<>("agentHistoryRecord", new RecordObjectValue<>(new AgentHistoryRecord()));

  public DbAgentHistoryV1() {
    super(1);
    declareProperty(recordProp);
  }

  public AgentHistoryRecord getRecord() {
    return recordProp.getValue().get();
  }

  public void setRecord(final AgentHistoryRecord record) {
    recordProp.getValue().get().copyFrom(record);
  }
}
