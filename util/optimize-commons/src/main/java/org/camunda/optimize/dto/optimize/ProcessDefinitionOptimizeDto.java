/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class ProcessDefinitionOptimizeDto extends DefinitionOptimizeResponseDto {
  private String bpmn20Xml;
  private Map<String, String> flowNodeNames = new HashMap<>();
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
                                      final String engine,
                                      final String tenantId) {
    super(id, key, version, versionTag, name, engine, tenantId, false, DefinitionType.PROCESS);
  }

  public ProcessDefinitionOptimizeDto(final String id,
                                      final String engine,
                                      final String bpmn20Xml,
                                      final Map<String, String> flowNodeNames,
                                      final Map<String, String> userTaskNames) {
    super(id, engine);
    this.bpmn20Xml = bpmn20Xml;
    this.flowNodeNames = flowNodeNames;
    this.userTaskNames = userTaskNames;
  }

  @Builder
  public ProcessDefinitionOptimizeDto(final String id,
                                      final String key,
                                      final String version,
                                      final String versionTag,
                                      final String name,
                                      final String engine,
                                      final String tenantId,
                                      final String bpmn20Xml,
                                      final boolean deleted,
                                      final Map<String, String> flowNodeNames,
                                      final Map<String, String> userTaskNames) {
    super(id, key, version, versionTag, name, engine, tenantId, deleted, DefinitionType.PROCESS);
    this.bpmn20Xml = bpmn20Xml;
    this.flowNodeNames = flowNodeNames;
    this.userTaskNames = userTaskNames;
  }

  public Map<String, String> getFlowNodeNames() {
    return flowNodeNames == null
      ? new HashMap<>()
      : new HashMap<>(flowNodeNames);
  }

  public Map<String, String> getUserTaskNames() {
    return userTaskNames == null
      ? new HashMap<>()
      : new HashMap<>(userTaskNames);
  }
}
