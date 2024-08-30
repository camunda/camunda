/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ExternalProcessVariableDto implements OptimizeDto {

  private String variableId;
  private String variableName;
  private String variableValue;
  private VariableType variableType;
  private Long ingestionTimestamp;
  private String processInstanceId;
  private String processDefinitionKey;
  private String serializationDataFormat; // optional, used for object variables

  public static final class Fields {

    public static final String variableId = "variableId";
    public static final String variableName = "variableName";
    public static final String variableValue = "variableValue";
    public static final String variableType = "variableType";
    public static final String ingestionTimestamp = "ingestionTimestamp";
    public static final String processInstanceId = "processInstanceId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String serializationDataFormat = "serializationDataFormat";
  }
}
