/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.tasklist;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class TaskEntity
    implements ExporterEntity<TaskEntity>, PartitionedEntity<TaskEntity>, TenantOwned {

  private String id;

  private long key;

  private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  private int partitionId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String flowNodeBpmnId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String flowNodeInstanceId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private OffsetDateTime completionTime;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String processInstanceId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long position;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TaskState state;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private OffsetDateTime creationTime;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String bpmnProcessId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String processDefinitionId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String assignee;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String[] candidateGroups;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String[] candidateUsers;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String formKey;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String formId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long formVersion;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean isFormEmbedded;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private OffsetDateTime followUpDate;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private OffsetDateTime dueDate;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String externalFormReference;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer processDefinitionVersion;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, String> customHeaders;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer priority;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String action;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> changedAttributes;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TaskJoinRelationship join;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TaskImplementation implementation;

  public TaskEntity() {}

  @Override
  public String getId() {
    return id;
  }

  @Override
  public TaskEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public TaskEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public TaskEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public TaskEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public TaskEntity setFlowNodeBpmnId(final String flowNodeBpmnId) {
    this.flowNodeBpmnId = flowNodeBpmnId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public TaskEntity setFlowNodeInstanceId(final String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public OffsetDateTime getCompletionTime() {
    return completionTime;
  }

  public TaskEntity setCompletionTime(final OffsetDateTime completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskEntity setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public TaskEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public TaskState getState() {
    return state;
  }

  public TaskEntity setState(final TaskState state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public TaskEntity setCreationTime(final OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskEntity setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskEntity setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskEntity setCandidateGroups(final String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskEntity setCandidateUsers(final String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskEntity setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskEntity setFormId(final String formId) {
    this.formId = formId;
    return this;
  }

  public Long getFormVersion() {
    return formVersion;
  }

  public TaskEntity setFormVersion(final Long formVersion) {
    this.formVersion = formVersion;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public TaskEntity setIsFormEmbedded(final Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskEntity setFollowUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskEntity setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public String getExternalFormReference() {
    return externalFormReference;
  }

  public TaskEntity setExternalFormReference(final String externalFormReference) {
    this.externalFormReference = externalFormReference;
    return this;
  }

  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public TaskEntity setProcessDefinitionVersion(final Integer processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  public TaskEntity setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
    return this;
  }

  public Integer getPriority() {
    return priority;
  }

  public TaskEntity setPriority(final Integer priority) {
    this.priority = priority;
    return this;
  }

  public String getAction() {
    return action;
  }

  public TaskEntity setAction(final String action) {
    this.action = action;
    return this;
  }

  public List<String> getChangedAttributes() {
    return changedAttributes;
  }

  public TaskEntity setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
    return this;
  }

  public TaskJoinRelationship getJoin() {
    return join;
  }

  public TaskEntity setJoin(final TaskJoinRelationship join) {
    this.join = join;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskEntity setImplementation(final TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public enum TaskImplementation {
    JOB_WORKER,
    ZEEBE_USER_TASK
  }
}
