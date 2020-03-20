/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class EventProcessInstanceDto extends ProcessInstanceDto {
  private List<FlowNodeInstanceUpdateDto> pendingFlowNodeInstanceUpdates = new ArrayList<>();
  private Map<String, EventCorrelationStateDto> correlatedEventsById = new HashMap<>();

  @Builder(builderMethodName = "eventProcessInstanceBuilder")
  public EventProcessInstanceDto(final String processDefinitionKey,
                                 final String processDefinitionVersion,
                                 final String processDefinitionId,
                                 final String processInstanceId,
                                 final Long duration,
                                 final String state,
                                 final OffsetDateTime startDate,
                                 final OffsetDateTime endDate,
                                 final List<FlowNodeInstanceDto> events,
                                 final List<SimpleProcessVariableDto> variables,
                                 final List<FlowNodeInstanceUpdateDto> pendingFlowNodeInstanceUpdates) {
    super(
      processDefinitionKey,
      processDefinitionVersion,
      processDefinitionId,
      processInstanceId,
      null,
      startDate,
      endDate,
      duration,
      state,
      Optional.ofNullable(events).orElseGet(ArrayList::new),
      new ArrayList<>(),
      variables,
      null,
      null
    );
    this.pendingFlowNodeInstanceUpdates = Optional.ofNullable(pendingFlowNodeInstanceUpdates).orElseGet(ArrayList::new);
  }
}
