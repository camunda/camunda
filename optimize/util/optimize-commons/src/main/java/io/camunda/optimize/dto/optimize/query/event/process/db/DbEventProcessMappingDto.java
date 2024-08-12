/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.db;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DbEventProcessMappingDto implements OptimizeDto {

  @EqualsAndHashCode.Include private String id;
  private String name;
  private String xml;
  private OffsetDateTime lastModified;
  private String lastModifier;
  private List<DbEventMappingDto> mappings;
  private List<EventSourceEntryDto<?>> eventSources;
  private List<EventProcessRoleRequestDto<IdentityDto>> roles;

  public static DbEventProcessMappingDto fromEventProcessMappingDto(
      final EventProcessMappingDto eventMappingDto) {
    return DbEventProcessMappingDto.builder()
        .id(eventMappingDto.getId())
        .name(eventMappingDto.getName())
        .xml(eventMappingDto.getXml())
        .lastModified(eventMappingDto.getLastModified())
        .lastModifier(eventMappingDto.getLastModifier())
        .mappings(
            Optional.ofNullable(eventMappingDto.getMappings())
                .map(
                    mappings ->
                        mappings.keySet().stream()
                            .map(
                                flowNodeId ->
                                    DbEventMappingDto.fromEventMappingDto(
                                        flowNodeId, eventMappingDto.getMappings().get(flowNodeId)))
                            .collect(Collectors.toList()))
                .orElse(null))
        .eventSources(eventMappingDto.getEventSources())
        .roles(eventMappingDto.getRoles())
        .build();
  }

  public EventProcessMappingDto toEventProcessMappingDto() {
    return EventProcessMappingDto.builder()
        .id(id)
        .name(name)
        .xml(xml)
        .lastModified(lastModified)
        .lastModifier(lastModifier)
        .mappings(
            Optional.ofNullable(mappings)
                .map(
                    mappingList ->
                        mappingList.stream()
                            .collect(
                                Collectors.toMap(
                                    DbEventMappingDto::getFlowNodeId,
                                    mapping ->
                                        EventMappingDto.builder()
                                            .start(mapping.getStart())
                                            .end(mapping.getEnd())
                                            .build())))
                .orElse(null))
        .eventSources(eventSources)
        .roles(roles)
        .build();
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String name = "name";
    public static final String xml = "xml";
    public static final String lastModified = "lastModified";
    public static final String lastModifier = "lastModifier";
    public static final String mappings = "mappings";
    public static final String eventSources = "eventSources";
    public static final String roles = "roles";
  }
}
