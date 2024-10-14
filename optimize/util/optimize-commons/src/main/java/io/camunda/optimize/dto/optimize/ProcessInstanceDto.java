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
    if (b.flowNodeInstances$set) {
      flowNodeInstances = b.flowNodeInstances$value;
    } else {
      flowNodeInstances = $default$flowNodeInstances();
    }
    if (b.variables$set) {
      variables = b.variables$value;
    } else {
      variables = $default$variables();
    }
    if (b.incidents$set) {
      incidents = b.incidents$value;
    } else {
      incidents = $default$incidents();
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
    final int PRIME = 59;
    int result = 1;
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $processDefinitionVersion = getProcessDefinitionVersion();
    result =
        result * PRIME
            + ($processDefinitionVersion == null ? 43 : $processDefinitionVersion.hashCode());
    final Object $processDefinitionId = getProcessDefinitionId();
    result = result * PRIME + ($processDefinitionId == null ? 43 : $processDefinitionId.hashCode());
    final Object $processInstanceId = getProcessInstanceId();
    result = result * PRIME + ($processInstanceId == null ? 43 : $processInstanceId.hashCode());
    final Object $businessKey = getBusinessKey();
    result = result * PRIME + ($businessKey == null ? 43 : $businessKey.hashCode());
    final Object $startDate = getStartDate();
    result = result * PRIME + ($startDate == null ? 43 : $startDate.hashCode());
    final Object $endDate = getEndDate();
    result = result * PRIME + ($endDate == null ? 43 : $endDate.hashCode());
    final Object $duration = getDuration();
    result = result * PRIME + ($duration == null ? 43 : $duration.hashCode());
    final Object $state = getState();
    result = result * PRIME + ($state == null ? 43 : $state.hashCode());
    final Object $flowNodeInstances = getFlowNodeInstances();
    result = result * PRIME + ($flowNodeInstances == null ? 43 : $flowNodeInstances.hashCode());
    final Object $variables = getVariables();
    result = result * PRIME + ($variables == null ? 43 : $variables.hashCode());
    final Object $incidents = getIncidents();
    result = result * PRIME + ($incidents == null ? 43 : $incidents.hashCode());
    final Object $dataSource = getDataSource();
    result = result * PRIME + ($dataSource == null ? 43 : $dataSource.hashCode());
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessInstanceDto)) {
      return false;
    }
    final ProcessInstanceDto other = (ProcessInstanceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$processDefinitionVersion = getProcessDefinitionVersion();
    final Object other$processDefinitionVersion = other.getProcessDefinitionVersion();
    if (this$processDefinitionVersion == null
        ? other$processDefinitionVersion != null
        : !this$processDefinitionVersion.equals(other$processDefinitionVersion)) {
      return false;
    }
    final Object this$processDefinitionId = getProcessDefinitionId();
    final Object other$processDefinitionId = other.getProcessDefinitionId();
    if (this$processDefinitionId == null
        ? other$processDefinitionId != null
        : !this$processDefinitionId.equals(other$processDefinitionId)) {
      return false;
    }
    final Object this$processInstanceId = getProcessInstanceId();
    final Object other$processInstanceId = other.getProcessInstanceId();
    if (this$processInstanceId == null
        ? other$processInstanceId != null
        : !this$processInstanceId.equals(other$processInstanceId)) {
      return false;
    }
    final Object this$businessKey = getBusinessKey();
    final Object other$businessKey = other.getBusinessKey();
    if (this$businessKey == null
        ? other$businessKey != null
        : !this$businessKey.equals(other$businessKey)) {
      return false;
    }
    final Object this$startDate = getStartDate();
    final Object other$startDate = other.getStartDate();
    if (this$startDate == null
        ? other$startDate != null
        : !this$startDate.equals(other$startDate)) {
      return false;
    }
    final Object this$endDate = getEndDate();
    final Object other$endDate = other.getEndDate();
    if (this$endDate == null ? other$endDate != null : !this$endDate.equals(other$endDate)) {
      return false;
    }
    final Object this$duration = getDuration();
    final Object other$duration = other.getDuration();
    if (this$duration == null ? other$duration != null : !this$duration.equals(other$duration)) {
      return false;
    }
    final Object this$state = getState();
    final Object other$state = other.getState();
    if (this$state == null ? other$state != null : !this$state.equals(other$state)) {
      return false;
    }
    final Object this$flowNodeInstances = getFlowNodeInstances();
    final Object other$flowNodeInstances = other.getFlowNodeInstances();
    if (this$flowNodeInstances == null
        ? other$flowNodeInstances != null
        : !this$flowNodeInstances.equals(other$flowNodeInstances)) {
      return false;
    }
    final Object this$variables = getVariables();
    final Object other$variables = other.getVariables();
    if (this$variables == null
        ? other$variables != null
        : !this$variables.equals(other$variables)) {
      return false;
    }
    final Object this$incidents = getIncidents();
    final Object other$incidents = other.getIncidents();
    if (this$incidents == null
        ? other$incidents != null
        : !this$incidents.equals(other$incidents)) {
      return false;
    }
    final Object this$dataSource = getDataSource();
    final Object other$dataSource = other.getDataSource();
    if (this$dataSource == null
        ? other$dataSource != null
        : !this$dataSource.equals(other$dataSource)) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    return true;
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

  private static List<FlowNodeInstanceDto> $default$flowNodeInstances() {
    return new ArrayList<>();
  }

  private static List<SimpleProcessVariableDto> $default$variables() {
    return new ArrayList<>();
  }

  private static List<IncidentDto> $default$incidents() {
    return new ArrayList<>();
  }

  public static ProcessInstanceDtoBuilder<?, ?> builder() {
    return new ProcessInstanceDtoBuilderImpl();
  }

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
    private List<FlowNodeInstanceDto> flowNodeInstances$value;
    private boolean flowNodeInstances$set;
    private List<SimpleProcessVariableDto> variables$value;
    private boolean variables$set;
    private List<IncidentDto> incidents$value;
    private boolean incidents$set;
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
      flowNodeInstances$value = flowNodeInstances;
      flowNodeInstances$set = true;
      return self();
    }

    public B variables(final List<SimpleProcessVariableDto> variables) {
      variables$value = variables;
      variables$set = true;
      return self();
    }

    public B incidents(final List<IncidentDto> incidents) {
      incidents$value = incidents;
      incidents$set = true;
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
          + ", flowNodeInstances$value="
          + flowNodeInstances$value
          + ", variables$value="
          + variables$value
          + ", incidents$value="
          + incidents$value
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
