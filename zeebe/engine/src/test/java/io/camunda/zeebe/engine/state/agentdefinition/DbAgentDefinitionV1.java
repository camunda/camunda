/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agentdefinition;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.agentdefinition.AgentDefinitionRecordV1;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class DbAgentDefinitionV1 extends UnpackedObject implements DbValue {

  private final ObjectProperty<AgentDefinitionRecordV1> recordProp =
      new ObjectProperty<>("agentDefinitionRecord", new AgentDefinitionRecordV1());

  public DbAgentDefinitionV1() {
    super(1);
    declareProperty(recordProp);
  }

  public AgentDefinitionRecordV1 getRecord() {
    return recordProp.getValue();
  }

  public void setRecord(final AgentDefinitionRecordV1 record) {
    recordProp.getValue().copyFrom(record);
  }
}
