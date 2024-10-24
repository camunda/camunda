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
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProcessInstanceDto implements OptimizeDto {

  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String processDefinitionId;
  private String processInstanceId;
  private String businessKey;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Long duration; // duration in ms
  private String state;
  private List<FlowNodeInstanceDto> flowNodeInstances = new ArrayList<>();
  private List<SimpleProcessVariableDto> variables = new ArrayList<>();
  private List<IncidentDto> incidents = new ArrayList<>();
  private DataSourceDto dataSource;
  private String tenantId;

  protected ProcessInstanceDto(
      final String processDefinitionKey,
      final String processDefinitionVersion,
      final String processDefinitionId,
      final String processInstanceId,
      final String businessKey,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final Long duration,
      final String state,
      final List<FlowNodeInstanceDto> flowNodeInstances,
      final List<SimpleProcessVariableDto> variables,
      final List<IncidentDto> incidents,
      final DataSourceDto dataSource,
      final String tenantId) {
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersion = processDefinitionVersion;
    this.processDefinitionId = processDefinitionId;
    this.processInstanceId = processInstanceId;
    this.businessKey = businessKey;
    this.startDate = startDate;
    this.endDate = endDate;
    this.duration = duration;
    this.state = state;
    this.flowNodeInstances = flowNodeInstances;
    this.variables = variables;
    this.incidents = incidents;
    this.dataSource = dataSource;
    this.tenantId = tenantId;
  }

  public ProcessInstanceDto() {}

  protected ProcessInstanceDto(final ProcessInstanceDtoBuilder<?, ?> b) {
    processDefinitionKey = b.processDefinitionKey;
    processDefinitionVersion = b.processDefinitionVersion;
    processDefinitionId = b.processDefinitionId;
    processInstanceId = b.processInstanceId;
    businessKey = b.businessKey;
    startDate = b.startDate;
    endDate = b.endDate;
    duration = b.duration;
    state = b.state;
    if (b.flowNodeInstancesSet) {
      flowNodeInstances = b.flowNodeInstancesValue;
    } else {
      flowNodeInstances = defaultFlowNodeInstances();
    }
    if (b.variablesSet) {
      variables = b.variablesValue;
    } else {
      variables = defaultVariables();
    }
    if (b.incidentsSet) {
      incidents = b.incidentsValue;
    } else {
      incidents = defaultIncidents();
    }
    dataSource = b.dataSource;
    tenantId = b.tenantId;
  }

  @JsonIgnore
  public List<FlowNodeInstanceDto> getUserTasks() {
    return flowNodeInstances.stream()
        .filter(flowNode -> FLOW_NODE_TYPE_USER_TASK.equalsIgnoreCase(flowNode.getFlowNodeType()))
        .toList();
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(final String businessKey) {
    this.businessKey = businessKey;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public Long getDuration() {
    return duration;
  }

  public void setDuration(final Long duration) {
    this.duration = duration;
  }

  public String getState() {
    return state;
  }

  public void setState(final String state) {
    this.state = state;
  }

  public List<FlowNodeInstanceDto> getFlowNodeInstances() {
    return flowNodeInstances;
  }

  public void setFlowNodeInstances(final List<FlowNodeInstanceDto> flowNodeInstances) {
    this.flowNodeInstances = flowNodeInstances;
  }

  public List<SimpleProcessVariableDto> getVariables() {
    return variables;
  }

  public void setVariables(final List<SimpleProcessVariableDto> variables) {
    this.variables = variables;
  }

  public List<IncidentDto> getIncidents() {
    return incidents;
  }

  public void setIncidents(final List<IncidentDto> incidents) {
    this.incidents = incidents;
  }

  public DataSourceDto getDataSource() {
    return dataSource;
  }

  public void setDataSource(final DataSourceDto dataSource) {
    this.dataSource = dataSource;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessInstanceDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "ProcessInstanceDto(processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processDefinitionVersion="
        + getProcessDefinitionVersion()
        + ", processDefinitionId="
        + getProcessDefinitionId()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ", businessKey="
        + getBusinessKey()
        + ", startDate="
        + getStartDate()
        + ", endDate="
        + getEndDate()
        + ", duration="
        + getDuration()
        + ", state="
        + getState()
        + ", flowNodeInstances="
        + getFlowNodeInstances()
        + ", variables="
        + getVariables()
        + ", incidents="
        + getIncidents()
        + ", dataSource="
        + getDataSource()
        + ", tenantId="
        + getTenantId()
        + ")";
  }

  private static List<FlowNodeInstanceDto> defaultFlowNodeInstances() {
    return new ArrayList<>();
  }

  private static List<SimpleProcessVariableDto> defaultVariables() {
    return new ArrayList<>();
  }

  private static List<IncidentDto> defaultIncidents() {
    return new ArrayList<>();
  }

  public static ProcessInstanceDtoBuilder<?, ?> builder() {
    return new ProcessInstanceDtoBuilderImpl();
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String processDefinitionVersion = "processDefinitionVersion";
    public static final String processDefinitionId = "processDefinitionId";
    public static final String processInstanceId = "processInstanceId";
    public static final String businessKey = "businessKey";
    public static final String startDate = "startDate";
    public static final String endDate = "endDate";
    public static final String duration = "duration";
    public static final String state = "state";
    public static final String flowNodeInstances = "flowNodeInstances";
    public static final String variables = "variables";
    public static final String incidents = "incidents";
    public static final String dataSource = "dataSource";
    public static final String tenantId = "tenantId";
  }

  public abstract static class ProcessInstanceDtoBuilder<
      C extends ProcessInstanceDto, B extends ProcessInstanceDtoBuilder<C, B>> {

    private String processDefinitionKey;
    private String processDefinitionVersion;
    private String processDefinitionId;
    private String processInstanceId;
    private String businessKey;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private Long duration;
    private String state;
    private List<FlowNodeInstanceDto> flowNodeInstancesValue;
    private boolean flowNodeInstancesSet;
    private List<SimpleProcessVariableDto> variablesValue;
    private boolean variablesSet;
    private List<IncidentDto> incidentsValue;
    private boolean incidentsSet;
    private DataSourceDto dataSource;
    private String tenantId;

    public B processDefinitionKey(final String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return self();
    }

    public B processDefinitionVersion(final String processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return self();
    }

    public B processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return self();
    }

    public B processInstanceId(final String processInstanceId) {
      this.processInstanceId = processInstanceId;
      return self();
    }

    public B businessKey(final String businessKey) {
      this.businessKey = businessKey;
      return self();
    }

    public B startDate(final OffsetDateTime startDate) {
      this.startDate = startDate;
      return self();
    }

    public B endDate(final OffsetDateTime endDate) {
      this.endDate = endDate;
      return self();
    }

    public B duration(final Long duration) {
      this.duration = duration;
      return self();
    }

    public B state(final String state) {
      this.state = state;
      return self();
    }

    public B flowNodeInstances(final List<FlowNodeInstanceDto> flowNodeInstances) {
      flowNodeInstancesValue = flowNodeInstances;
      flowNodeInstancesSet = true;
      return self();
    }

    public B variables(final List<SimpleProcessVariableDto> variables) {
      variablesValue = variables;
      variablesSet = true;
      return self();
    }

    public B incidents(final List<IncidentDto> incidents) {
      incidentsValue = incidents;
      incidentsSet = true;
      return self();
    }

    public B dataSource(final DataSourceDto dataSource) {
      this.dataSource = dataSource;
      return self();
    }

    public B tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return self();
    }

    protected abstract B self();

    public abstract C build();

    @Override
    public String toString() {
      return "ProcessInstanceDto.ProcessInstanceDtoBuilder(processDefinitionKey="
          + processDefinitionKey
          + ", processDefinitionVersion="
          + processDefinitionVersion
          + ", processDefinitionId="
          + processDefinitionId
          + ", processInstanceId="
          + processInstanceId
          + ", businessKey="
          + businessKey
          + ", startDate="
          + startDate
          + ", endDate="
          + endDate
          + ", duration="
          + duration
          + ", state="
          + state
          + ", flowNodeInstancesValue="
          + flowNodeInstancesValue
          + ", variablesValue="
          + variablesValue
          + ", incidentsValue="
          + incidentsValue
          + ", dataSource="
          + dataSource
          + ", tenantId="
          + tenantId
          + ")";
    }
  }

  private static final class ProcessInstanceDtoBuilderImpl
      extends ProcessInstanceDtoBuilder<ProcessInstanceDto, ProcessInstanceDtoBuilderImpl> {

    private ProcessInstanceDtoBuilderImpl() {}

    @Override
    protected ProcessInstanceDtoBuilderImpl self() {
      return this;
    }

    @Override
    public ProcessInstanceDto build() {
      return new ProcessInstanceDto(this);
    }
  }
}
