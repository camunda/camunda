/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.db;

import io.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DbEventProcessPublishStateDto {

  @EqualsAndHashCode.Include private String id;
  private String processMappingId;
  private String name;
  private OffsetDateTime publishDateTime;
  private EventProcessState state;
  private Double publishProgress;
  @Builder.Default private Boolean deleted = false;
  private String xml;
  private List<DbEventMappingDto> mappings;
  private List<EventImportSourceDto> eventImportSources;

  public static DbEventProcessPublishStateDto fromEventProcessPublishStateDto(
      final EventProcessPublishStateDto publishState) {
    return DbEventProcessPublishStateDto.builder()
        .id(publishState.getId())
        .processMappingId(publishState.getProcessMappingId())
        .name(publishState.getName())
        .xml(publishState.getXml())
        .publishDateTime(publishState.getPublishDateTime())
        .state(publishState.getState())
        .publishProgress(publishState.getPublishProgress())
        .deleted(publishState.getDeleted())
        .mappings(
            Optional.ofNullable(publishState.getMappings())
                .map(
                    mappings ->
                        mappings.keySet().stream()
                            .map(
                                flowNodeId ->
                                    DbEventMappingDto.fromEventMappingDto(
                                        flowNodeId, publishState.getMappings().get(flowNodeId)))
                            .collect(Collectors.toList()))
                .orElse(null))
        .eventImportSources(publishState.getEventImportSources())
        .build();
  }

  public EventProcessPublishStateDto toEventProcessPublishStateDto() {
    return EventProcessPublishStateDto.builder()
        .id(getId())
        .processMappingId(getProcessMappingId())
        .name(getName())
        .xml(getXml())
        .publishDateTime(getPublishDateTime())
        .state(getState())
        .publishProgress(getPublishProgress())
        .deleted(getDeleted())
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
        .eventImportSources(getEventImportSources())
        .build();
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String processMappingId = "processMappingId";
    public static final String name = "name";
    public static final String publishDateTime = "publishDateTime";
    public static final String state = "state";
    public static final String publishProgress = "publishProgress";
    public static final String deleted = "deleted";
    public static final String xml = "xml";
    public static final String mappings = "mappings";
    public static final String eventImportSources = "eventImportSources";
  }
}
