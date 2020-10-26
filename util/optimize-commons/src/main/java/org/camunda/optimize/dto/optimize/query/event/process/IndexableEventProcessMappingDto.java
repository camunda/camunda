/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;

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
public class IndexableEventProcessMappingDto implements OptimizeDto {
  @EqualsAndHashCode.Include
  private String id;
  private String name;
  private String xml;
  private OffsetDateTime lastModified;
  private String lastModifier;
  private List<IndexableEventMappingDto> mappings;
  private List<EventSourceEntryDto> eventSources;
  private List<EventProcessRoleRequestDto<IdentityDto>> roles;

  public static IndexableEventProcessMappingDto fromEventProcessMappingDto(final EventProcessMappingDto eventMappingDto) {
    return IndexableEventProcessMappingDto.builder()
      .id(eventMappingDto.getId())
      .name(eventMappingDto.getName())
      .xml(eventMappingDto.getXml())
      .lastModified(eventMappingDto.getLastModified())
      .lastModifier(eventMappingDto.getLastModifier())
      .mappings(
        Optional.ofNullable(eventMappingDto.getMappings())
          .map(mappings -> mappings.keySet()
            .stream()
            .map(flowNodeId -> IndexableEventMappingDto.fromEventMappingDto(
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
              IndexableEventMappingDto::getFlowNodeId,
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
