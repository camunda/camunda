/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class ProcessDefinitionOptimizeDto extends DefinitionOptimizeResponseDto {
  private String bpmn20Xml;
  private List<FlowNodeDataDto> flowNodeData = new ArrayList<>();
  private Map<String, String> userTaskNames = new HashMap<>();
  @JsonIgnore
  private boolean eventBased;

  public ProcessDefinitionOptimizeDto() {
    this.setType(DefinitionType.PROCESS);
  }

  public ProcessDefinitionOptimizeDto(final String id,
                                      final String key,
                                      final String version,
                                      final String versionTag,
                                      final String name,
                                      final DataSourceDto dataSource,
                                      final String tenantId) {
    super(id, key, version, versionTag, name, dataSource, tenantId, false, DefinitionType.PROCESS);
  }

  public ProcessDefinitionOptimizeDto(final String id,
                                      final DataSourceDto dataSource,
                                      final String bpmn20Xml,
                                      final List<FlowNodeDataDto> flowNodeData,
                                      final Map<String, String> userTaskNames) {
    super(id, dataSource);
    this.bpmn20Xml = bpmn20Xml;
    this.flowNodeData = flowNodeData;
    this.userTaskNames = userTaskNames;
  }

  @Builder
  public ProcessDefinitionOptimizeDto(final String id,
                                      final String key,
                                      final String version,
                                      final String versionTag,
                                      final String name,
                                      final DataSourceDto dataSource,
                                      final String tenantId,
                                      final String bpmn20Xml,
                                      final boolean deleted,
                                      final List<FlowNodeDataDto> flowNodeData,
                                      final Map<String, String> userTaskNames) {
    super(id, key, version, versionTag, name, dataSource, tenantId, deleted, DefinitionType.PROCESS);
    this.bpmn20Xml = bpmn20Xml;
    this.flowNodeData = flowNodeData;
    this.userTaskNames = userTaskNames;
  }

  public final List<FlowNodeDataDto> getFlowNodeData() {
    return flowNodeData == null
      ? new ArrayList<>()
      : flowNodeData;
  }

  @JsonIgnore
  public final List<FlowNodeDataDto> getUserTaskData() {
    return flowNodeData == null
      ? List.of()
      : flowNodeData.stream()
      .filter(flowNode -> FLOW_NODE_TYPE_USER_TASK.equalsIgnoreCase(flowNode.getType()))
      .collect(toList());
  }

  public Map<String, String> getUserTaskNames() {
    return userTaskNames == null
      ? new HashMap<>()
      : new HashMap<>(userTaskNames);
  }

}
