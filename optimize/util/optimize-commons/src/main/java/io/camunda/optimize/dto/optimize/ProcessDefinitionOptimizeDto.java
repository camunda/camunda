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

public class ProcessDefinitionOptimizeDto extends DefinitionOptimizeResponseDto {

  private String bpmn20Xml;
  private List<FlowNodeDataDto> flowNodeData = new ArrayList<>();
  private Map<String, String> userTaskNames = new HashMap<>();
  private boolean onboarded = false;

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

  public ProcessDefinitionOptimizeDto(
      final String bpmn20Xml,
      final List<FlowNodeDataDto> flowNodeData,
      final Map<String, String> userTaskNames,
      final boolean onboarded) {
    this.bpmn20Xml = bpmn20Xml;
    this.flowNodeData = flowNodeData;
    this.userTaskNames = userTaskNames;
    this.onboarded = onboarded;
  }

  public final List<FlowNodeDataDto> getFlowNodeData() {
    return flowNodeData == null ? new ArrayList<>() : flowNodeData;
  }

  public void setFlowNodeData(final List<FlowNodeDataDto> flowNodeData) {
    this.flowNodeData = flowNodeData;
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

  public void setUserTaskNames(final Map<String, String> userTaskNames) {
    this.userTaskNames = userTaskNames;
  }

  public String getBpmn20Xml() {
    return bpmn20Xml;
  }

  public void setBpmn20Xml(final String bpmn20Xml) {
    this.bpmn20Xml = bpmn20Xml;
  }

  public boolean isOnboarded() {
    return onboarded;
  }

  public void setOnboarded(final boolean onboarded) {
    this.onboarded = onboarded;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ProcessDefinitionOptimizeDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $bpmn20Xml = getBpmn20Xml();
    result = result * PRIME + ($bpmn20Xml == null ? 43 : $bpmn20Xml.hashCode());
    final Object $flowNodeData = getFlowNodeData();
    result = result * PRIME + ($flowNodeData == null ? 43 : $flowNodeData.hashCode());
    final Object $userTaskNames = getUserTaskNames();
    result = result * PRIME + ($userTaskNames == null ? 43 : $userTaskNames.hashCode());
    result = result * PRIME + (isOnboarded() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessDefinitionOptimizeDto)) {
      return false;
    }
    final ProcessDefinitionOptimizeDto other = (ProcessDefinitionOptimizeDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$bpmn20Xml = getBpmn20Xml();
    final Object other$bpmn20Xml = other.getBpmn20Xml();
    if (this$bpmn20Xml == null
        ? other$bpmn20Xml != null
        : !this$bpmn20Xml.equals(other$bpmn20Xml)) {
      return false;
    }
    final Object this$flowNodeData = getFlowNodeData();
    final Object other$flowNodeData = other.getFlowNodeData();
    if (this$flowNodeData == null
        ? other$flowNodeData != null
        : !this$flowNodeData.equals(other$flowNodeData)) {
      return false;
    }
    final Object this$userTaskNames = getUserTaskNames();
    final Object other$userTaskNames = other.getUserTaskNames();
    if (this$userTaskNames == null
        ? other$userTaskNames != null
        : !this$userTaskNames.equals(other$userTaskNames)) {
      return false;
    }
    if (isOnboarded() != other.isOnboarded()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ProcessDefinitionOptimizeDto(bpmn20Xml="
        + getBpmn20Xml()
        + ", flowNodeData="
        + getFlowNodeData()
        + ", userTaskNames="
        + getUserTaskNames()
        + ", onboarded="
        + isOnboarded()
        + ")";
  }

  public static ProcessDefinitionOptimizeDtoBuilder builder() {
    return new ProcessDefinitionOptimizeDtoBuilder();
  }

  public static final class Fields {

    public static final String bpmn20Xml = "bpmn20Xml";
    public static final String flowNodeData = "flowNodeData";
    public static final String userTaskNames = "userTaskNames";
    public static final String onboarded = "onboarded";
    public static final String eventBased = "eventBased";
  }

  public static class ProcessDefinitionOptimizeDtoBuilder {

    private String id;
    private String key;
    private String version;
    private String versionTag;
    private String name;
    private DataSourceDto dataSource;
    private String tenantId;
    private String bpmn20Xml;
    private boolean deleted;
    private boolean onboarded;
    private List<FlowNodeDataDto> flowNodeData;
    private Map<String, String> userTaskNames;

    ProcessDefinitionOptimizeDtoBuilder() {}

    public ProcessDefinitionOptimizeDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public ProcessDefinitionOptimizeDtoBuilder key(final String key) {
      this.key = key;
      return this;
    }

    public ProcessDefinitionOptimizeDtoBuilder version(final String version) {
      this.version = version;
      return this;
    }

    public ProcessDefinitionOptimizeDtoBuilder versionTag(final String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    public ProcessDefinitionOptimizeDtoBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public ProcessDefinitionOptimizeDtoBuilder dataSource(final DataSourceDto dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    public ProcessDefinitionOptimizeDtoBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public ProcessDefinitionOptimizeDtoBuilder bpmn20Xml(final String bpmn20Xml) {
      this.bpmn20Xml = bpmn20Xml;
      return this;
    }

    public ProcessDefinitionOptimizeDtoBuilder deleted(final boolean deleted) {
      this.deleted = deleted;
      return this;
    }

    public ProcessDefinitionOptimizeDtoBuilder onboarded(final boolean onboarded) {
      this.onboarded = onboarded;
      return this;
    }

    public ProcessDefinitionOptimizeDtoBuilder flowNodeData(
        final List<FlowNodeDataDto> flowNodeData) {
      this.flowNodeData = flowNodeData;
      return this;
    }

    public ProcessDefinitionOptimizeDtoBuilder userTaskNames(
        final Map<String, String> userTaskNames) {
      this.userTaskNames = userTaskNames;
      return this;
    }

    public ProcessDefinitionOptimizeDto build() {
      return new ProcessDefinitionOptimizeDto(
          id,
          key,
          version,
          versionTag,
          name,
          dataSource,
          tenantId,
          bpmn20Xml,
          deleted,
          onboarded,
          flowNodeData,
          userTaskNames);
    }

    @Override
    public String toString() {
      return "ProcessDefinitionOptimizeDto.ProcessDefinitionOptimizeDtoBuilder(id="
          + id
          + ", key="
          + key
          + ", version="
          + version
          + ", versionTag="
          + versionTag
          + ", name="
          + name
          + ", dataSource="
          + dataSource
          + ", tenantId="
          + tenantId
          + ", bpmn20Xml="
          + bpmn20Xml
          + ", deleted="
          + deleted
          + ", onboarded="
          + onboarded
          + ", flowNodeData="
          + flowNodeData
          + ", userTaskNames="
          + userTaskNames
          + ")";
    }
  }
}
