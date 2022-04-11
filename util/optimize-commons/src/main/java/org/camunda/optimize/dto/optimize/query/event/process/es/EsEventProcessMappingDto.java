/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@FieldNameConstants
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EsEventProcessMappingDto implements OptimizeDto {
  @EqualsAndHashCode.Include
  private String id;
  private String name;
  private String xml;
  private OffsetDateTime lastModified;
  private String lastModifier;
  private List<EsEventMappingDto> mappings;
  private List<EventSourceEntryDto<?>> eventSources;
  private List<EventProcessRoleRequestDto<IdentityDto>> roles;

  public static EsEventProcessMappingDto fromEventProcessMappingDto(final EventProcessMappingDto eventMappingDto) {
    return EsEventProcessMappingDto.builder()
      .id(eventMappingDto.getId())
      .name(eventMappingDto.getName())
      .xml(eventMappingDto.getXml())
      .lastModified(eventMappingDto.getLastModified())
      .lastModifier(eventMappingDto.getLastModifier())
      .mappings(
        Optional.ofNullable(eventMappingDto.getMappings())
          .map(mappings -> mappings.keySet()
            .stream()
            .map(flowNodeId -> EsEventMappingDto.fromEventMappingDto(
              flowNodeId,
              eventMappingDto.getMappings().get(flowNodeId)
            ))
            .collect(Collectors.toList()))
          .orElse(null)
      )
      .eventSources(eventMappingDto.getEventSources())
      .roles(eventMappingDto.getRoles())
      .build();
  }

  public EventProcessMappingDto toEventProcessMappingDto() {
    return EventProcessMappingDto.builder()
      .id(this.id)
      .name(this.name)
      .xml(this.xml)
      .lastModified(this.lastModified)
      .lastModifier(this.lastModifier)
      .mappings(
        Optional.ofNullable(this.mappings)
          .map(mappingList -> mappingList.stream()
            .collect(Collectors.toMap(
              EsEventMappingDto::getFlowNodeId,
              mapping -> EventMappingDto.builder()
                .start(mapping.getStart())
                .end(mapping.getEnd()).build()
            ))).orElse(null)
      )
      .eventSources(this.eventSources)
      .roles(this.roles)
      .build();
  }

}
