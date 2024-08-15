/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public class EventProcessPublishStateDto {

  @EqualsAndHashCode.Include private String id;
  private String processMappingId;
  private String name;
  private OffsetDateTime publishDateTime;
  private EventProcessState state;
  private Double publishProgress;
  @Builder.Default private Boolean deleted = false;
  private String xml;
  private Map<String, EventMappingDto> mappings;
  @Builder.Default private List<EventImportSourceDto> eventImportSources = new ArrayList<>();

  @JsonIgnore
  public String getProcessKey() {
    return processMappingId;
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
