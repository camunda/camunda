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

public class EventProcessDefinitionDto extends ProcessDefinitionOptimizeDto {

  public EventProcessDefinitionDto(
      final String id,
      final String key,
      final String version,
      final String versionTag,
      final String name,
      final String tenantId,
      final String bpmn20Xml,
      final boolean deleted,
      final boolean onboarded,
      final List<FlowNodeDataDto> flowNodeData,
      final Map<String, String> userTaskNames) {
    super(id, key, version, versionTag, name, new EventsDataSourceDto(), tenantId, bpmn20Xml,
        deleted, onboarded, flowNodeData, userTaskNames);
    if (id == null) {
      throw new IllegalArgumentException("id cannot be null");
    }

    if (key == null) {
      throw new IllegalArgumentException("key cannot be null");
    }

    if (version == null) {
      throw new IllegalArgumentException("version cannot be null");
    }

    if (name == null) {
      throw new IllegalArgumentException("name cannot be null");
    }

    if (bpmn20Xml == null) {
      throw new IllegalArgumentException("bpmn20Xml cannot be null");
    }

    if (flowNodeData == null) {
      throw new IllegalArgumentException("flowNodeData cannot be null");
    }

    if (userTaskNames == null) {
      throw new IllegalArgumentException("userTaskNames cannot be null");
    }
  }

  public EventProcessDefinitionDto() {
  }

  @Override
  public String toString() {
    return "EventProcessDefinitionDto()";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventProcessDefinitionDto)) {
      return false;
    }
    final EventProcessDefinitionDto other = (EventProcessDefinitionDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof EventProcessDefinitionDto;
  }

  @Override
  public int hashCode() {
    final int result = super.hashCode();
    return result;
  }

  public static EventProcessDefinitionDtoBuilder eventProcessBuilder() {
    return new EventProcessDefinitionDtoBuilder();
  }

  public static class EventProcessDefinitionDtoBuilder {

    private String id;
    private String key;
    private String version;
    private String versionTag;
    private String name;
    private String tenantId;
    private String bpmn20Xml;
    private boolean deleted;
    private boolean onboarded;
    private List<FlowNodeDataDto> flowNodeData;
    private Map<String, String> userTaskNames;

    EventProcessDefinitionDtoBuilder() {
    }

    public EventProcessDefinitionDtoBuilder id(final String id) {
      if (id == null) {
        throw new IllegalArgumentException("id cannot be null");
      }

      this.id = id;
      return this;
    }

    public EventProcessDefinitionDtoBuilder key(final String key) {
      if (key == null) {
        throw new IllegalArgumentException("key cannot be null");
      }

      this.key = key;
      return this;
    }

    public EventProcessDefinitionDtoBuilder version(final String version) {
      if (version == null) {
        throw new IllegalArgumentException("version cannot be null");
      }

      this.version = version;
      return this;
    }

    public EventProcessDefinitionDtoBuilder versionTag(final String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    public EventProcessDefinitionDtoBuilder name(final String name) {
      if (name == null) {
        throw new IllegalArgumentException("name cannot be null");
      }

      this.name = name;
      return this;
    }

    public EventProcessDefinitionDtoBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public EventProcessDefinitionDtoBuilder bpmn20Xml(final String bpmn20Xml) {
      if (bpmn20Xml == null) {
        throw new IllegalArgumentException("bpmn20Xml cannot be null");
      }

      this.bpmn20Xml = bpmn20Xml;
      return this;
    }

    public EventProcessDefinitionDtoBuilder deleted(final boolean deleted) {
      this.deleted = deleted;
      return this;
    }

    public EventProcessDefinitionDtoBuilder onboarded(final boolean onboarded) {
      this.onboarded = onboarded;
      return this;
    }

    public EventProcessDefinitionDtoBuilder flowNodeData(final List<FlowNodeDataDto> flowNodeData) {
      if (flowNodeData == null) {
        throw new IllegalArgumentException("flowNodeData cannot be null");
      }

      this.flowNodeData = flowNodeData;
      return this;
    }

    public EventProcessDefinitionDtoBuilder userTaskNames(final Map<String, String> userTaskNames) {
      if (userTaskNames == null) {
        throw new IllegalArgumentException("userTaskNames cannot be null");
      }

      this.userTaskNames = userTaskNames;
      return this;
    }

    public EventProcessDefinitionDto build() {
      return new EventProcessDefinitionDto(id, key, version, versionTag,
          name, tenantId, bpmn20Xml, deleted, onboarded, flowNodeData,
          userTaskNames);
    }

    @Override
    public String toString() {
      return "EventProcessDefinitionDto.EventProcessDefinitionDtoBuilder(id=" + id + ", key="
          + key + ", version=" + version + ", versionTag=" + versionTag + ", name="
          + name + ", tenantId=" + tenantId + ", bpmn20Xml=" + bpmn20Xml
          + ", deleted=" + deleted + ", onboarded=" + onboarded + ", flowNodeData="
          + flowNodeData + ", userTaskNames=" + userTaskNames + ")";
    }
  }
}
