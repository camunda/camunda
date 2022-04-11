/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Data
@Builder
@FieldNameConstants
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EventProcessPublishStateDto {
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
  private Map<String, EventMappingDto> mappings;
  @Builder.Default
  private List<EventImportSourceDto> eventImportSources = new ArrayList<>();

  @JsonIgnore
  public String getProcessKey() {
    return processMappingId;
  }
}
