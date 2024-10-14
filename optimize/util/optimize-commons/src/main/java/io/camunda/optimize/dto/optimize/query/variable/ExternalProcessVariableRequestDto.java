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

  public void setId(@NotBlank String id) {
    this.id = id;
  }

  public void setName(@NotBlank String name) {
    this.name = name;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public void setType(@NotNull VariableType type) {
    this.type = type;
  }

  public void setProcessInstanceId(@NotBlank String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public void setProcessDefinitionKey(@NotBlank String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setSerializationDataFormat(String serializationDataFormat) {
    this.serializationDataFormat = serializationDataFormat;
  }

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
    final Object this$id = this.getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$name = this.getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$value = this.getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    final Object this$type = this.getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$processInstanceId = this.getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$processDefinitionKey = this.getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$serializationDataFormat = this.getSerializationDataFormat();
    final Object other$serializationDataFormat = other.getSerializationDataFormat();
    if (this$serializationDataFormat == null
        ? other$serializationDataFormat != null
        : !this$serializationDataFormat.equals(other$serializationDataFormat)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExternalProcessVariableRequestDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = this.getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $name = this.getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $value = this.getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    final Object $type = this.getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $processInstanceId = this.getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $processDefinitionKey = this.getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $serializationDataFormat = this.getSerializationDataFormat();
    result =
        result * PRIME
            + ($serializationDataFormat == null ? 43 : $serializationDataFormat.hashCode());
    return result;
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
