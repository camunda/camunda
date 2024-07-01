/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class EventProcessMappingCreateRequestDto extends EventProcessMappingRequestDto {

  private boolean autogenerate = false;

  private static final String DEFAULT_PROCESS_NAME = "New Process";

  @Builder(builderMethodName = "eventProcessMappingCreateBuilder")
  public EventProcessMappingCreateRequestDto(
      String name,
      String xml,
      Map<String, EventMappingDto> mappings,
      List<EventSourceEntryDto<?>> eventSources,
      boolean autogenerate) {
    super(name, xml, mappings, eventSources);
    this.autogenerate = autogenerate;
  }

  public static EventProcessMappingDto to(
      final String userId, final EventProcessMappingCreateRequestDto createRequestDto) {
    return EventProcessMappingDto.builder()
        .name(Optional.ofNullable(createRequestDto.getName()).orElse(DEFAULT_PROCESS_NAME))
        .xml(createRequestDto.getXml())
        .mappings(createRequestDto.getMappings())
        .lastModifier(userId)
        .eventSources(createRequestDto.getEventSources())
        .roles(
            Collections.singletonList(
                new EventProcessRoleRequestDto<>(new IdentityDto(userId, IdentityType.USER))))
        .build();
  }
}
