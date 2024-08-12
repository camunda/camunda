/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessState;
import io.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public class EventProcessMappingResponseDto {

  @EqualsAndHashCode.Include private String id;
  @NotBlank private String name;

  private String lastModifier;

  private OffsetDateTime lastModified;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String xml;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Valid
  private Map<String, EventMappingDto> mappings;

  private EventProcessState state;

  private Double publishingProgress;

  @Builder.Default private List<EventSourceEntryDto<?>> eventSources = new ArrayList<>();

  public static EventProcessMappingResponseDto from(
      final EventProcessMappingDto dto,
      final String lastModifierName,
      final List<EventSourceEntryDto<?>> eventSourcesDtos) {
    return EventProcessMappingResponseDto.builder()
        .id(dto.getId())
        .lastModified(dto.getLastModified())
        .lastModifier(lastModifierName)
        .mappings(dto.getMappings())
        .name(dto.getName())
        .state(dto.getState())
        .publishingProgress(dto.getPublishingProgress())
        .xml(dto.getXml())
        .eventSources(eventSourcesDtos)
        .build();
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String name = "name";
    public static final String lastModifier = "lastModifier";
    public static final String lastModified = "lastModified";
    public static final String xml = "xml";
    public static final String mappings = "mappings";
    public static final String state = "state";
    public static final String publishingProgress = "publishingProgress";
    public static final String eventSources = "eventSources";
  }
}
