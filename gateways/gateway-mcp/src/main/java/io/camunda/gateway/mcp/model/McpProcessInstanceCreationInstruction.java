/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationStartInstruction;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationTerminateInstruction;
import java.util.List;

public class McpProcessInstanceCreationInstruction extends ProcessInstanceCreationInstruction {

  @JsonIgnore
  @Override
  public Long getOperationReference() {
    return super.getOperationReference();
  }

  @JsonIgnore
  @Override
  public List<ProcessInstanceCreationStartInstruction> getStartInstructions() {
    return super.getStartInstructions();
  }

  @JsonIgnore
  @Override
  public List<ProcessInstanceCreationTerminateInstruction> getRuntimeInstructions() {
    return super.getRuntimeInstructions();
  }
}
