/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(builderMethodName = "eventProcessInstanceBuilder")
public class EventProcessInstanceDto extends ProcessInstanceDto {

  @Builder.Default
  private List<FlowNodeInstanceUpdateDto> pendingFlowNodeInstanceUpdates = new ArrayList<>();

  @Builder.Default
  private Map<String, EventCorrelationStateDto> correlatedEventsById = new HashMap<>();

  public static final class Fields {

    public static final String pendingFlowNodeInstanceUpdates = "pendingFlowNodeInstanceUpdates";
    public static final String correlatedEventsById = "correlatedEventsById";
  }
}
