/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@FieldNameConstants
public class EventToFlowNodeMapping {
  @EqualsAndHashCode.Include
  private String eventIdentifier;
  private MappedEventType mappedAs;
  private String flowNodeId;
  private String flowNodeType;
  private List<String> previousMappedFlowNodeIds;
  private List<String> nextMappedFlowNodeIds;
}
