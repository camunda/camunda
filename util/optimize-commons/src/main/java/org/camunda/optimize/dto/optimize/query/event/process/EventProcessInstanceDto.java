/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(builderMethodName = "eventProcessInstanceBuilder")
@FieldNameConstants
public class EventProcessInstanceDto extends ProcessInstanceDto {
  @Builder.Default
  private List<FlowNodeInstanceUpdateDto> pendingFlowNodeInstanceUpdates = new ArrayList<>();
  @Builder.Default
  private Map<String, EventCorrelationStateDto> correlatedEventsById = new HashMap<>();
}
