/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventSourceEntryDto;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class EventProcessMappingCreateRequestDto extends EventProcessMappingRequestDto {

  private boolean autogenerate = false;

  @Builder(builderMethodName = "eventProcessMappingCreateBuilder")
  public EventProcessMappingCreateRequestDto(String name,
                                             String xml,
                                             Map<String, EventMappingDto> mappings,
                                             List<EventSourceEntryDto> eventSources,
                                             boolean autogenerate) {
    super(name, xml, mappings, eventSources);
    this.autogenerate = autogenerate;
  }

}
