/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.event.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@FieldNameConstants
public class EventProcessMappingRequestDto {
  @NotBlank
  private String name;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String xml;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Valid
  private Map<String, EventMappingDto> mappings;

  @Builder.Default
  private List<EventSourceEntryDto> eventSources = new ArrayList<>();
}
