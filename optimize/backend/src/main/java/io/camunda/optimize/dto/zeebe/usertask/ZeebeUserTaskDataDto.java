/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe.usertask;

import io.camunda.optimize.service.util.DateFormatterUtil;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import net.minidev.json.annotate.JsonIgnore;
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
  public int getPriority() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  public long getUserTaskKey() {
    return this.userTaskKey;
  }

  public String getAssignee() {
    return this.assignee;
  }

  public List<String> getCandidateGroupsList() {
    return this.candidateGroupsList;
  }

  public List<String> getCandidateUsersList() {
    return this.candidateUsersList;
  }

  public String getDueDate() {
    return this.dueDate;
  }

  public String getElementId() {
    return this.elementId;
  }

  public long getElementInstanceKey() {
    return this.elementInstanceKey;
  }

  public String getBpmnProcessId() {
    return this.bpmnProcessId;
  }

  public int getProcessDefinitionVersion() {
    return this.processDefinitionVersion;
  }

  public long getProcessDefinitionKey() {
    return this.processDefinitionKey;
  }

  public long getProcessInstanceKey() {
    return this.processInstanceKey;
  }

  public String getTenantId() {
    return this.tenantId;
  }

  public List<String> getChangedAttributes() {
    return this.changedAttributes;
  }

  public Map<String, Object> getVariables() {
    return this.variables;
  }

  public String getFollowUpDate() {
    return this.followUpDate;
  }

  public long getFormKey() {
    return this.formKey;
  }

  public String getAction() {
    return this.action;
  }

  public String getExternalFormReference() {
    return this.externalFormReference;
  }

  public Map<String, String> getCustomHeaders() {
    return this.customHeaders;
  }

  public long getCreationTimestamp() {
    return this.creationTimestamp;
  }

  public void setUserTaskKey(final long userTaskKey) {
    this.userTaskKey = userTaskKey;
  }

  public void setAssignee(final String assignee) {
    this.assignee = assignee;
  }

  public void setCandidateGroupsList(final List<String> candidateGroupsList) {
    this.candidateGroupsList = candidateGroupsList;
  }

  public void setCandidateUsersList(final List<String> candidateUsersList) {
    this.candidateUsersList = candidateUsersList;
  }

  public void setDueDate(final String dueDate) {
    this.dueDate = dueDate;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public void setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
  }

  public void setVariables(final Map<String, Object> variables) {
    this.variables = variables;
  }

  public void setFollowUpDate(final String followUpDate) {
    this.followUpDate = followUpDate;
  }

  public void setFormKey(final long formKey) {
    this.formKey = formKey;
  }

  public void setAction(final String action) {
    this.action = action;
  }

  public void setExternalFormReference(final String externalFormReference) {
    this.externalFormReference = externalFormReference;
  }

  public void setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
  }

  public void setCreationTimestamp(final long creationTimestamp) {
    this.creationTimestamp = creationTimestamp;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeUserTaskDataDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  public String toString() {
    return "ZeebeUserTaskDataDto(userTaskKey="
        + this.getUserTaskKey()
        + ", assignee="
        + this.getAssignee()
        + ", candidateGroupsList="
        + this.getCandidateGroupsList()
        + ", candidateUsersList="
        + this.getCandidateUsersList()
        + ", dueDate="
        + this.getDueDate()
        + ", elementId="
        + this.getElementId()
        + ", elementInstanceKey="
        + this.getElementInstanceKey()
        + ", bpmnProcessId="
        + this.getBpmnProcessId()
        + ", processDefinitionVersion="
        + this.getProcessDefinitionVersion()
        + ", processDefinitionKey="
        + this.getProcessDefinitionKey()
        + ", processInstanceKey="
        + this.getProcessInstanceKey()
        + ", tenantId="
        + this.getTenantId()
        + ", changedAttributes="
        + this.getChangedAttributes()
        + ", variables="
        + this.getVariables()
        + ", followUpDate="
        + this.getFollowUpDate()
        + ", formKey="
        + this.getFormKey()
        + ", action="
        + this.getAction()
        + ", externalFormReference="
        + this.getExternalFormReference()
        + ", customHeaders="
        + this.getCustomHeaders()
        + ", creationTimestamp="
        + this.getCreationTimestamp()
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
  }
}
