/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessDefinitionOptimizeDto extends DefinitionOptimizeResponseDto {

  private String bpmn20Xml;
  private List<FlowNodeDataDto> flowNodeData = new ArrayList<>();
  private Map<String, String> userTaskNames = new HashMap<>();
  private boolean onboarded = false;
  @JsonIgnore private boolean eventBased;

  public ProcessDefinitionOptimizeDto() {
    setType(DefinitionType.PROCESS);
  }

  public ProcessDefinitionOptimizeDto(
      final String id,
      final String key,
      final String version,
      final String versionTag,
      final String name,
      final boolean onboarded,
      final DataSourceDto dataSource,
      final String tenantId) {
    super(id, key, version, versionTag, name, dataSource, tenantId, false, DefinitionType.PROCESS);
    this.onboarded = onboarded;
  }

  public ProcessDefinitionOptimizeDto(
      final String id,
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
  public ProcessDefinitionOptimizeDto(
      final String id,
      final String key,
      final String version,
      final String versionTag,
      final String name,
      final DataSourceDto dataSource,
      final String tenantId,
      final String bpmn20Xml,
      final boolean deleted,
      final boolean onboarded,
      final List<FlowNodeDataDto> flowNodeData,
      final Map<String, String> userTaskNames) {
    super(
        id, key, version, versionTag, name, dataSource, tenantId, deleted, DefinitionType.PROCESS);
    this.bpmn20Xml = bpmn20Xml;
    this.flowNodeData = flowNodeData;
    this.userTaskNames = userTaskNames;
    this.onboarded = onboarded;
  }

  public final List<FlowNodeDataDto> getFlowNodeData() {
    return flowNodeData == null ? new ArrayList<>() : flowNodeData;
  }

  @JsonIgnore
  public final List<FlowNodeDataDto> getUserTaskData() {
    return flowNodeData == null
        ? List.of()
        : flowNodeData.stream()
            .filter(flowNode -> FLOW_NODE_TYPE_USER_TASK.equalsIgnoreCase(flowNode.getType()))
            .toList();
  }

  public Map<String, String> getUserTaskNames() {
    return userTaskNames == null ? new HashMap<>() : new HashMap<>(userTaskNames);
  }

  public static final class Fields {

    public static final String bpmn20Xml = "bpmn20Xml";
    public static final String flowNodeData = "flowNodeData";
    public static final String userTaskNames = "userTaskNames";
    public static final String onboarded = "onboarded";
    public static final String eventBased = "eventBased";
  }
}
