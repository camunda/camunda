/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.usertask;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.service.util.DateFormatterUtil;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;

public class ZeebeUserTaskDataDto implements UserTaskRecordValue {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ZeebeUserTaskDataDto.class);
  private long userTaskKey;
  private String assignee;
  private List<String> candidateGroupsList;
  private List<String> candidateUsersList;
  private String dueDate;
  private String elementId;
  private long elementInstanceKey;
  private String bpmnProcessId;
  private int processDefinitionVersion;
  private long processDefinitionKey;
  private long processInstanceKey;
  private String tenantId;
  private List<String> changedAttributes;
  private Map<String, Object> variables;
  private String followUpDate;
  private long formKey;
  private String action;
  private String externalFormReference;
  private Map<String, String> customHeaders;
  private long creationTimestamp;
  private Set<String> tags;

  public ZeebeUserTaskDataDto() {}

  @JsonIgnore
  public OffsetDateTime getDateForDueDate() {
    return DateFormatterUtil.getOffsetDateTimeFromIsoZoneDateTimeString(dueDate)
        .orElseGet(
            () -> {
              LOG.info(
                  "Unable to parse due date of userTask record: {}. UserTask will be imported without dueDate data.",
                  dueDate);
              return null;
            });
  }

  @Override
  public long getUserTaskKey() {
    return userTaskKey;
  }

  @Override
  public String getAssignee() {
    return assignee;
  }

  @Override
  public List<String> getCandidateGroupsList() {
    return candidateGroupsList;
  }

  @Override
  public List<String> getCandidateUsersList() {
    return candidateUsersList;
  }

  @Override
  public String getDueDate() {
    return dueDate;
  }

  @Override
  public String getFollowUpDate() {
    return followUpDate;
  }

  @Override
  public long getFormKey() {
    return formKey;
  }

  @Override
  public List<String> getChangedAttributes() {
    return changedAttributes;
  }

  @Override
  public String getAction() {
    return action;
  }

  @Override
  public String getExternalFormReference() {
    return externalFormReference;
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  @Override
  public long getCreationTimestamp() {
    return creationTimestamp;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public int getPriority() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public Set<String> getTags() {
    return tags;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return -1L; // not used in Optimize
  }

  public void setTags(final Set<String> tags) {
    this.tags = tags;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  public void setCreationTimestamp(final long creationTimestamp) {
    this.creationTimestamp = creationTimestamp;
  }

  public void setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
  }

  public void setExternalFormReference(final String externalFormReference) {
    this.externalFormReference = externalFormReference;
  }

  public void setAction(final String action) {
    this.action = action;
  }

  public void setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
  }

  public void setFormKey(final long formKey) {
    this.formKey = formKey;
  }

  public void setFollowUpDate(final String followUpDate) {
    this.followUpDate = followUpDate;
  }

  public void setDueDate(final String dueDate) {
    this.dueDate = dueDate;
  }

  public void setCandidateUsersList(final List<String> candidateUsersList) {
    this.candidateUsersList = candidateUsersList;
  }

  public void setCandidateGroupsList(final List<String> candidateGroupsList) {
    this.candidateGroupsList = candidateGroupsList;
  }

  public void setAssignee(final String assignee) {
    this.assignee = assignee;
  }

  public void setUserTaskKey(final long userTaskKey) {
    this.userTaskKey = userTaskKey;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(final Map<String, Object> variables) {
    this.variables = variables;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeUserTaskDataDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeUserTaskDataDto that = (ZeebeUserTaskDataDto) o;
    return userTaskKey == that.userTaskKey
        && elementInstanceKey == that.elementInstanceKey
        && processDefinitionVersion == that.processDefinitionVersion
        && processDefinitionKey == that.processDefinitionKey
        && processInstanceKey == that.processInstanceKey
        && formKey == that.formKey
        && creationTimestamp == that.creationTimestamp
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(candidateGroupsList, that.candidateGroupsList)
        && Objects.equals(candidateUsersList, that.candidateUsersList)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(changedAttributes, that.changedAttributes)
        && Objects.equals(variables, that.variables)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(action, that.action)
        && Objects.equals(externalFormReference, that.externalFormReference)
        && Objects.equals(customHeaders, that.customHeaders)
        && Objects.equals(tags, that.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        userTaskKey,
        assignee,
        candidateGroupsList,
        candidateUsersList,
        dueDate,
        elementId,
        elementInstanceKey,
        bpmnProcessId,
        processDefinitionVersion,
        processDefinitionKey,
        processInstanceKey,
        tenantId,
        changedAttributes,
        variables,
        followUpDate,
        formKey,
        action,
        externalFormReference,
        customHeaders,
        creationTimestamp,
        tags);
  }

  @Override
  public String toString() {
    return "ZeebeUserTaskDataDto(userTaskKey="
        + getUserTaskKey()
        + ", assignee="
        + getAssignee()
        + ", candidateGroupsList="
        + getCandidateGroupsList()
        + ", candidateUsersList="
        + getCandidateUsersList()
        + ", dueDate="
        + getDueDate()
        + ", elementId="
        + getElementId()
        + ", elementInstanceKey="
        + getElementInstanceKey()
        + ", bpmnProcessId="
        + getBpmnProcessId()
        + ", processDefinitionVersion="
        + getProcessDefinitionVersion()
        + ", processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processInstanceKey="
        + getProcessInstanceKey()
        + ", tenantId="
        + getTenantId()
        + ", changedAttributes="
        + getChangedAttributes()
        + ", variables="
        + getVariables()
        + ", followUpDate="
        + getFollowUpDate()
        + ", formKey="
        + getFormKey()
        + ", action="
        + getAction()
        + ", externalFormReference="
        + getExternalFormReference()
        + ", customHeaders="
        + getCustomHeaders()
        + ", creationTimestamp="
        + getCreationTimestamp()
        + ", tags="
        + getTags()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String userTaskKey = "userTaskKey";
    public static final String assignee = "assignee";
    public static final String candidateGroupsList = "candidateGroupsList";
    public static final String candidateUsersList = "candidateUsersList";
    public static final String dueDate = "dueDate";
    public static final String elementId = "elementId";
    public static final String elementInstanceKey = "elementInstanceKey";
    public static final String bpmnProcessId = "bpmnProcessId";
    public static final String processDefinitionVersion = "processDefinitionVersion";
    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String processInstanceKey = "processInstanceKey";
    public static final String tenantId = "tenantId";
    public static final String changedAttributes = "changedAttributes";
    public static final String variables = "variables";
    public static final String followUpDate = "followUpDate";
    public static final String formKey = "formKey";
    public static final String action = "action";
    public static final String externalFormReference = "externalFormReference";
    public static final String customHeaders = "customHeaders";
    public static final String creationTimestamp = "creationTimestamp";
    public static final String tags = "tags";
  }
}
