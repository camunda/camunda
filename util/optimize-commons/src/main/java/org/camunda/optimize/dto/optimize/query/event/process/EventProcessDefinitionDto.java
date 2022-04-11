/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EventsDataSourceDto;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class EventProcessDefinitionDto extends ProcessDefinitionOptimizeDto {

  @Builder(builderMethodName = "eventProcessBuilder")
  public EventProcessDefinitionDto(@NonNull final String id, @NonNull final String key,
                                   @NonNull final String version,
                                   final String versionTag, @NonNull final String name,
                                   final String tenantId, @NonNull final String bpmn20Xml, final boolean deleted,
                                   @NonNull final List<FlowNodeDataDto> flowNodeData,
                                   @NonNull final Map<String, String> userTaskNames) {
    super(id, key, version, versionTag, name, new EventsDataSourceDto(),
          tenantId, bpmn20Xml, deleted, flowNodeData, userTaskNames
    );
  }
}
