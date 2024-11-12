/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class ExternalProcessVariableRequestDto implements OptimizeDto {

  @NotBlank private String id;
  @NotBlank private String name;
  private String value;
  @NotNull private VariableType type;
  @NotBlank private String processInstanceId;
  @NotBlank private String processDefinitionKey;
  private String serializationDataFormat; // optional, used for object variables

  public ExternalProcessVariableRequestDto() {}

  public static List<ExternalProcessVariableDto> toExternalProcessVariableDtos(
      final Long ingestionTimestamp, final List<ExternalProcessVariableRequestDto> variableDtos) {
    return variableDtos.stream()
        .map(
            varDto -> {
              final ExternalProcessVariableDto externalProcessVariableDto =
                  new ExternalProcessVariableDto();
              externalProcessVariableDto.setIngestionTimestamp(ingestionTimestamp);
              externalProcessVariableDto.setVariableId(varDto.getId());
              externalProcessVariableDto.setVariableName(varDto.getName());
              externalProcessVariableDto.setVariableValue(varDto.getValue());
              externalProcessVariableDto.setVariableType(varDto.getType());
              externalProcessVariableDto.setProcessInstanceId(varDto.getProcessInstanceId());
              externalProcessVariableDto.setProcessDefinitionKey(varDto.getProcessDefinitionKey());
              externalProcessVariableDto.setSerializationDataFormat(
                  varDto.getSerializationDataFormat());
              return externalProcessVariableDto;
            })
        .toList();
  }

  public @NotBlank String getId() {
    return this.id;
  }

  public @NotBlank String getName() {
    return this.name;
  }

  public String getValue() {
    return this.value;
  }

  public @NotNull VariableType getType() {
    return this.type;
  }

  public @NotBlank String getProcessInstanceId() {
    return this.processInstanceId;
  }

  public @NotBlank String getProcessDefinitionKey() {
    return this.processDefinitionKey;
  }

  public String getSerializationDataFormat() {
    return this.serializationDataFormat;
  }

  public void setId(@NotBlank final String id) {
    this.id = id;
  }

  public void setName(@NotBlank final String name) {
    this.name = name;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  public void setType(@NotNull final VariableType type) {
    this.type = type;
  }

  public void setProcessInstanceId(@NotBlank final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public void setProcessDefinitionKey(@NotBlank final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setSerializationDataFormat(final String serializationDataFormat) {
    this.serializationDataFormat = serializationDataFormat;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExternalProcessVariableRequestDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  public String toString() {
    return "ExternalProcessVariableRequestDto(id="
        + this.getId()
        + ", name="
        + this.getName()
        + ", value="
        + this.getValue()
        + ", type="
        + this.getType()
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

    public static final String id = "id";
    public static final String name = "name";
    public static final String value = "value";
    public static final String type = "type";
    public static final String processInstanceId = "processInstanceId";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String serializationDataFormat = "serializationDataFormat";
  }
}
