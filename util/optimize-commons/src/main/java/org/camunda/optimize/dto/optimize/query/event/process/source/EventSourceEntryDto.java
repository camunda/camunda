/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event.process.source;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.service.util.IdGenerator;

import javax.validation.constraints.NotNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ExternalEventSourceEntryDto.class, name = "external"),
  @JsonSubTypes.Type(value = CamundaEventSourceEntryDto.class, name = "camunda")
})
@Data
@NoArgsConstructor
@FieldNameConstants
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class EventSourceEntryDto<CONFIG extends EventSourceConfigDto> {

  @EqualsAndHashCode.Include
  @NonNull
  @Builder.Default
  protected String id = IdGenerator.getNextId();

  @JsonIgnore
  public abstract EventSourceType getSourceType();

  @NotNull
  protected CONFIG configuration;

  @JsonIgnore
  public String getSourceIdentifier() {
    if (EventSourceType.CAMUNDA.equals(getSourceType())) {
      return getSourceType() + ":" + ((CamundaEventSourceConfigDto) configuration).getProcessDefinitionKey();
    } else {
      final ExternalEventSourceConfigDto externalSourceConfig = (ExternalEventSourceConfigDto) configuration;
      if (externalSourceConfig.isIncludeAllGroups()) {
        return getSourceType() + ":" + "allExternalEventGroups";
      } else {
        return getSourceType() + ":" + externalSourceConfig.getGroup();
      }
    }
  }

}

