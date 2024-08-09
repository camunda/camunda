/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.source;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.service.util.IdGenerator;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ExternalEventSourceEntryDto.class, name = "external"),
})
@Data
@NoArgsConstructor
@FieldNameConstants
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class EventSourceEntryDto<CONFIG extends EventSourceConfigDto> {

  public static final String TYPE = "type";

  @EqualsAndHashCode.Include @NonNull @Builder.Default
  protected String id = IdGenerator.getNextId();

  @NotNull protected CONFIG configuration;

  @JsonIgnore
  public abstract EventSourceType getSourceType();

  // This source identifier is only used internally by Optimize for logic such as autogeneration
  @JsonIgnore
  public String getSourceIdentifier() {
    final ExternalEventSourceConfigDto externalSourceConfig =
        (ExternalEventSourceConfigDto) configuration;
    if (externalSourceConfig.isIncludeAllGroups()) {
      return getSourceType() + ":" + "optimize_allExternalEventGroups";
    } else {
      return getSourceType()
          + ":"
          + Optional.ofNullable(externalSourceConfig.getGroup())
              .orElse("optimize_noGroupSpecified");
    }
  }
}
