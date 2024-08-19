/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import io.camunda.optimize.dto.optimize.OptimizeDto;

public class ExternalProcessVariableDto implements OptimizeDto {

  private String variableId;
  private String variableName;
  private String variableValue;
  private VariableType variableType;
  private Long ingestionTimestamp;
  private String processInstanceId;
  private String processDefinitionKey;
  private String serializationDataFormat; // optional, used for object variables

  public ExternalProcessVariableDto() {}

  public String getVariableId() {
    return variableId;
  }

  public ExternalProcessVariableDto setVariableId(final String variableId) {
    this.variableId = variableId;
    return this;
  }

  public String getVariableName() {
    return variableName;
  }

  public ExternalProcessVariableDto setVariableName(final String variableName) {
    this.variableName = variableName;
    return this;
  }

  public String getVariableValue() {
    return variableValue;
  }

  public ExternalProcessVariableDto setVariableValue(final String variableValue) {
    this.variableValue = variableValue;
    return this;
  }

  public VariableType getVariableType() {
    return variableType;
  }

  public ExternalProcessVariableDto setVariableType(final VariableType variableType) {
    this.variableType = variableType;
    return this;
  }

  public Long getIngestionTimestamp() {
    return ingestionTimestamp;
  }

  public ExternalProcessVariableDto setIngestionTimestamp(final Long ingestionTimestamp) {
    this.ingestionTimestamp = ingestionTimestamp;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public ExternalProcessVariableDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ExternalProcessVariableDto setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getSerializationDataFormat() {
    return serializationDataFormat;
  }

  public ExternalProcessVariableDto setSerializationDataFormat(
      final String serializationDataFormat) {
    this.serializationDataFormat = serializationDataFormat;
    return this;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExternalProcessVariableDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $variableId = getVariableId();
    result = result * PRIME + ($variableId == null ? 43 : $variableId.hashCode());
    final Object $variableName = getVariableName();
    result = result * PRIME + ($variableName == null ? 43 : $variableName.hashCode());
    final Object $variableValue = getVariableValue();
    result = result * PRIME + ($variableValue == null ? 43 : $variableValue.hashCode());
    final Object $variableType = getVariableType();
    result = result * PRIME + ($variableType == null ? 43 : $variableType.hashCode());
    final Object $ingestionTimestamp = getIngestionTimestamp();
    result = result * PRIME + ($ingestionTimestamp == null ? 43 : $ingestionTimestamp.hashCode());
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $serializationDataFormat = getSerializationDataFormat();
    result =
        result * PRIME
            + ($serializationDataFormat == null ? 43 : $serializationDataFormat.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ExternalProcessVariableDto)) {
      return false;
    }
    final ExternalProcessVariableDto other = (ExternalProcessVariableDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$variableId = getVariableId();
    final Object other$variableId = other.getVariableId();
    if (this$variableId == null
        ? other$variableId != null
        : !this$variableId.equals(other$variableId)) {
      return false;
    }
    final Object this$variableName = getVariableName();
    final Object other$variableName = other.getVariableName();
    if (this$variableName == null
        ? other$variableName != null
        : !this$variableName.equals(other$variableName)) {
      return false;
    }
    final Object this$variableValue = getVariableValue();
    final Object other$variableValue = other.getVariableValue();
    if (this$variableValue == null
        ? other$variableValue != null
        : !this$variableValue.equals(other$variableValue)) {
      return false;
    }
    final Object this$variableType = getVariableType();
    final Object other$variableType = other.getVariableType();
    if (this$variableType == null
        ? other$variableType != null
        : !this$variableType.equals(other$variableType)) {
      return false;
    }
    final Object this$ingestionTimestamp = getIngestionTimestamp();
    final Object other$ingestionTimestamp = other.getIngestionTimestamp();
    if (this$ingestionTimestamp == null
        ? other$ingestionTimestamp != null
        : !this$ingestionTimestamp.equals(other$ingestionTimestamp)) {
      return false;
    }
    final Object this$processInstanceId = getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$serializationDataFormat = getSerializationDataFormat();
    final Object other$serializationDataFormat = other.getSerializationDataFormat();
    if (this$serializationDataFormat == null
        ? other$serializationDataFormat != null
        : !this$serializationDataFormat.equals(other$serializationDataFormat)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ExternalProcessVariableDto(variableId="
        + getVariableId()
        + ", variableName="
        + getVariableName()
        + ", variableValue="
        + getVariableValue()
        + ", variableType="
        + getVariableType()
        + ", ingestionTimestamp="
        + getIngestionTimestamp()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", serializationDataFormat="
        + getSerializationDataFormat()
        + ")";
  }

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
