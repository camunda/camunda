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
    return this.variableId;
  }

  public String getVariableName() {
    return this.variableName;
  }

  public String getVariableValue() {
    return this.variableValue;
  }

  public VariableType getVariableType() {
    return this.variableType;
  }

  public Long getIngestionTimestamp() {
    return this.ingestionTimestamp;
  }

  public String getProcessInstanceId() {
    return this.processInstanceId;
  }

  public String getProcessDefinitionKey() {
    return this.processDefinitionKey;
  }

  public String getSerializationDataFormat() {
    return this.serializationDataFormat;
  }

  public void setVariableId(final String variableId) {
    this.variableId = variableId;
  }

  public void setVariableName(final String variableName) {
    this.variableName = variableName;
  }

  public void setVariableValue(final String variableValue) {
    this.variableValue = variableValue;
  }

  public void setVariableType(final VariableType variableType) {
    this.variableType = variableType;
  }

  public void setIngestionTimestamp(final Long ingestionTimestamp) {
    this.ingestionTimestamp = ingestionTimestamp;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setSerializationDataFormat(final String serializationDataFormat) {
    this.serializationDataFormat = serializationDataFormat;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExternalProcessVariableDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  public String toString() {
    return "ExternalProcessVariableDto(variableId="
        + this.getVariableId()
        + ", variableName="
        + this.getVariableName()
        + ", variableValue="
        + this.getVariableValue()
        + ", variableType="
        + this.getVariableType()
        + ", ingestionTimestamp="
        + this.getIngestionTimestamp()
        + ", processInstanceId="
        + this.getProcessInstanceId()
        + ", processDefinitionKey="
        + this.getProcessDefinitionKey()
        + ", serializationDataFormat="
        + this.getSerializationDataFormat()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
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
