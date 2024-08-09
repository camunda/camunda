/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.EventsDataSourceDto;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class EventProcessDefinitionDto extends ProcessDefinitionOptimizeDto {

  @Builder(builderMethodName = "eventProcessBuilder")
  public EventProcessDefinitionDto(
      @NonNull final String id,
      @NonNull final String key,
      @NonNull final String version,
      final String versionTag,
      @NonNull final String name,
      final String tenantId,
      @NonNull final String bpmn20Xml,
      final boolean deleted,
      final boolean onboarded,
      @NonNull final List<FlowNodeDataDto> flowNodeData,
      @NonNull final Map<String, String> userTaskNames) {
    super(
        id,
        key,
        version,
        versionTag,
        name,
        new EventsDataSourceDto(),
        tenantId,
        bpmn20Xml,
        deleted,
        onboarded,
        flowNodeData,
        userTaskNames);
  }
}
