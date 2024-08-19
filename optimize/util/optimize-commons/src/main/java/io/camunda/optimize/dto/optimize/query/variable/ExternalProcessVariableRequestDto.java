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
            varDto ->
                new ExternalProcessVariableDto()
                    .setIngestionTimestamp(ingestionTimestamp)
                    .setVariableId(varDto.getId())
                    .setVariableName(varDto.getName())
                    .setVariableValue(varDto.getValue())
                    .setVariableType(varDto.getType())
                    .setProcessInstanceId(varDto.getProcessInstanceId())
                    .setProcessDefinitionKey(varDto.getProcessDefinitionKey())
                    .setSerializationDataFormat(varDto.getSerializationDataFormat()))
        .toList();
  }

  public @NotBlank String getId() {
    return id;
  }

  public ExternalProcessVariableRequestDto setId(@NotBlank final String id) {
    this.id = id;
    return this;
  }

  public @NotBlank String getName() {
    return name;
  }

  public ExternalProcessVariableRequestDto setName(@NotBlank final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public ExternalProcessVariableRequestDto setValue(final String value) {
    this.value = value;
    return this;
  }

  public @NotNull VariableType getType() {
    return type;
  }

  public ExternalProcessVariableRequestDto setType(@NotNull final VariableType type) {
    this.type = type;
    return this;
  }

  public @NotBlank String getProcessInstanceId() {
    return processInstanceId;
  }

  public ExternalProcessVariableRequestDto setProcessInstanceId(
      @NotBlank final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public @NotBlank String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ExternalProcessVariableRequestDto setProcessDefinitionKey(
      @NotBlank final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getSerializationDataFormat() {
    return serializationDataFormat;
  }

  public ExternalProcessVariableRequestDto setSerializationDataFormat(
      final String serializationDataFormat) {
    this.serializationDataFormat = serializationDataFormat;
    return this;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExternalProcessVariableRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
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
    if (!(o instanceof ExternalProcessVariableRequestDto)) {
      return false;
    }
    final ExternalProcessVariableRequestDto other = (ExternalProcessVariableRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
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
    return "ExternalProcessVariableRequestDto(id="
        + getId()
        + ", name="
        + getName()
        + ", value="
        + getValue()
        + ", type="
        + getType()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", serializationDataFormat="
        + getSerializationDataFormat()
        + ")";
  }

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
