/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process.es;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.event.process.EventImportSourceDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessState;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Data
@Builder
@FieldNameConstants
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EsEventProcessPublishStateDto {
  @EqualsAndHashCode.Include
  private String id;
  private String processMappingId;
  private String name;
  private OffsetDateTime publishDateTime;
  private EventProcessState state;
  private Double publishProgress;
  @Builder.Default
  private Boolean deleted = false;
  private String xml;
  private List<EsEventMappingDto> mappings;
  private List<EventImportSourceDto> eventImportSources;

  public static EsEventProcessPublishStateDto fromEventProcessPublishStateDto(
    final EventProcessPublishStateDto publishState) {
    return EsEventProcessPublishStateDto.builder()
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
          .map(mappings -> mappings.keySet()
            .stream()
            .map(flowNodeId -> EsEventMappingDto.fromEventMappingDto(
              flowNodeId,
              publishState.getMappings().get(flowNodeId)
            ))
            .collect(Collectors.toList()))
          .orElse(null)
      )
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
        Optional.ofNullable(this.mappings)
          .map(mappingList -> mappingList.stream()
            .collect(Collectors.toMap(
              EsEventMappingDto::getFlowNodeId,
              mapping -> EventMappingDto.builder()
                .start(mapping.getStart())
                .end(mapping.getEnd()).build()
            ))).orElse(null)
      )
      .eventImportSources(getEventImportSources())
      .build();
  }
}
