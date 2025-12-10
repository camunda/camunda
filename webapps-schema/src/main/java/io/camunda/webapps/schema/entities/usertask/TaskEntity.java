/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usertask;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TaskEntity
    implements ExporterEntity<TaskEntity>, PartitionedEntity<TaskEntity>, TenantOwned {

  @BeforeVersion880 private String id;

  @BeforeVersion880 private long key;

  @BeforeVersion880 private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  @BeforeVersion880 private int partitionId;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String flowNodeBpmnId;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String name;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String flowNodeInstanceId;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private OffsetDateTime completionTime;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String processInstanceId;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long position;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TaskState state;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private OffsetDateTime creationTime;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String bpmnProcessId;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String processDefinitionId;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String assignee;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String[] candidateGroups;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String[] candidateUsers;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String formKey;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String formId;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long formVersion;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean isFormEmbedded;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private OffsetDateTime followUpDate;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private OffsetDateTime dueDate;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String externalFormReference;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer processDefinitionVersion;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, String> customHeaders;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer priority;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Set<String> tags;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String action;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> changedAttributes;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TaskJoinRelationship join;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TaskImplementation implementation;

  /** Attention! This field will be filled in only for data imported after v. 8.9.0. */
  @SinceVersion(value = "8.9.0", requireDefault = false)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long rootProcessInstanceKey;

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

  public String getName() {
    return name;
  }

  public TaskEntity setName(final String name) {
    this.name = name;
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

  public Set<String> getTags() {
    return tags;
  }

  public TaskEntity setTags(final Set<String> tags) {
    this.tags = tags;
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

  public TaskEntity addChangedAttribute(final String changedAttribute) {
    if (changedAttributes == null) {
      changedAttributes = new ArrayList<>();
    }
    changedAttributes.add(changedAttribute);
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

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public TaskEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public enum TaskImplementation {
    JOB_WORKER,
    ZEEBE_USER_TASK
  }
}
