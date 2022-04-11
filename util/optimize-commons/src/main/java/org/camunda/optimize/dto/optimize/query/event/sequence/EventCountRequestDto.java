/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.sequence;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;

import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventCountRequestDto {

  @Size(min = 1)
  private String targetFlowNodeId;

  @Size(min = 1)
  private String xml;

  @Builder.Default
  @NonNull
  private Map<String, EventMappingDto> mappings = new HashMap<>();

  @Builder.Default
  @NonNull
  private List<EventSourceEntryDto<?>> eventSources = new ArrayList<>();

}
