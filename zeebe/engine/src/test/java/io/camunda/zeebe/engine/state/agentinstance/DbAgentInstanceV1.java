/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agentinstance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.RecordObjectValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class DbAgentInstanceV1 extends UnpackedObject implements DbValue {

  private final ObjectProperty<RecordObjectValue<AgentInstanceRecord>> recordProp =
      new ObjectProperty<>("agentInstance", new RecordObjectValue<>(new AgentInstanceRecord()));

  public DbAgentInstanceV1() {
    super(1);
    declareProperty(recordProp);
  }

  public AgentInstanceRecord getRecord() {
    return recordProp.getValue().get();
  }

  public void setRecord(final AgentInstanceRecord record) {
    recordProp.getValue().get().copyFrom(record);
  }
}
