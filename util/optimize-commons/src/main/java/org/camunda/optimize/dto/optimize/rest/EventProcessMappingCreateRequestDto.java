/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class EventProcessMappingCreateRequestDto extends EventProcessMappingRequestDto {

  private boolean autogenerate = false;

  private static final String DEFAULT_PROCESS_NAME = "New Process";

  @Builder(builderMethodName = "eventProcessMappingCreateBuilder")
  public EventProcessMappingCreateRequestDto(String name,
                                             String xml,
                                             Map<String, EventMappingDto> mappings,
                                             List<EventSourceEntryDto<?>> eventSources,
                                             boolean autogenerate) {
    super(name, xml, mappings, eventSources);
    this.autogenerate = autogenerate;
  }

  public static EventProcessMappingDto to(final String userId,
                                          final EventProcessMappingCreateRequestDto createRequestDto) {
    return EventProcessMappingDto.builder()
      .name(Optional.ofNullable(createRequestDto.getName()).orElse(DEFAULT_PROCESS_NAME))
      .xml(createRequestDto.getXml())
      .mappings(createRequestDto.getMappings())
      .lastModifier(userId)
      .eventSources(createRequestDto.getEventSources())
      .roles(Collections.singletonList(new EventProcessRoleRequestDto<>(new IdentityDto(userId, IdentityType.USER))))
      .build();
  }

}
