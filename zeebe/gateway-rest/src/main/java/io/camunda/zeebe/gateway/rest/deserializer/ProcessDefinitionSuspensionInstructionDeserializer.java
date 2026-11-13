/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.ProcessDefinitionSuspensionInstruction;
import io.camunda.gateway.protocol.model.ProcessDefinitionSuspensionInstructionById;
import io.camunda.gateway.protocol.model.ProcessDefinitionSuspensionInstructionByKey;

public class ProcessDefinitionSuspensionInstructionDeserializer
    extends AbstractProcessDefinitionStateChangeInstructionDeserializer<
        ProcessDefinitionSuspensionInstruction> {

  @Override
  protected Class<? extends ProcessDefinitionSuspensionInstruction> byKeyType() {
    return ProcessDefinitionSuspensionInstructionByKey.class;
  }

  @Override
  protected Class<? extends ProcessDefinitionSuspensionInstruction> byIdType() {
    return ProcessDefinitionSuspensionInstructionById.class;
  }
}
