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

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ZeebeUserTaskDataDto.class);
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
              log.info(
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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final long $userTaskKey = getUserTaskKey();
    result = result * PRIME + (int) ($userTaskKey >>> 32 ^ $userTaskKey);
    final Object $assignee = getAssignee();
    result = result * PRIME + ($assignee == null ? 43 : $assignee.hashCode());
    final Object $candidateGroupsList = getCandidateGroupsList();
    result = result * PRIME + ($candidateGroupsList == null ? 43 : $candidateGroupsList.hashCode());
    final Object $candidateUsersList = getCandidateUsersList();
    result = result * PRIME + ($candidateUsersList == null ? 43 : $candidateUsersList.hashCode());
    final Object $dueDate = getDueDate();
    result = result * PRIME + ($dueDate == null ? 43 : $dueDate.hashCode());
    final Object $elementId = getElementId();
    result = result * PRIME + ($elementId == null ? 43 : $elementId.hashCode());
    final long $elementInstanceKey = getElementInstanceKey();
    result = result * PRIME + (int) ($elementInstanceKey >>> 32 ^ $elementInstanceKey);
    final Object $bpmnProcessId = getBpmnProcessId();
    result = result * PRIME + ($bpmnProcessId == null ? 43 : $bpmnProcessId.hashCode());
    result = result * PRIME + getProcessDefinitionVersion();
    final long $processDefinitionKey = getProcessDefinitionKey();
    result = result * PRIME + (int) ($processDefinitionKey >>> 32 ^ $processDefinitionKey);
    final long $processInstanceKey = getProcessInstanceKey();
    result = result * PRIME + (int) ($processInstanceKey >>> 32 ^ $processInstanceKey);
    final Object $tenantId = getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $changedAttributes = getChangedAttributes();
    result = result * PRIME + ($changedAttributes == null ? 43 : $changedAttributes.hashCode());
    final Object $variables = getVariables();
    result = result * PRIME + ($variables == null ? 43 : $variables.hashCode());
    final Object $followUpDate = getFollowUpDate();
    result = result * PRIME + ($followUpDate == null ? 43 : $followUpDate.hashCode());
    final long $formKey = getFormKey();
    result = result * PRIME + (int) ($formKey >>> 32 ^ $formKey);
    final Object $action = getAction();
    result = result * PRIME + ($action == null ? 43 : $action.hashCode());
    final Object $externalFormReference = getExternalFormReference();
    result =
        result * PRIME + ($externalFormReference == null ? 43 : $externalFormReference.hashCode());
    final Object $customHeaders = getCustomHeaders();
    result = result * PRIME + ($customHeaders == null ? 43 : $customHeaders.hashCode());
    final long $creationTimestamp = getCreationTimestamp();
    result = result * PRIME + (int) ($creationTimestamp >>> 32 ^ $creationTimestamp);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ZeebeUserTaskDataDto)) {
      return false;
    }
    final ZeebeUserTaskDataDto other = (ZeebeUserTaskDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getUserTaskKey() != other.getUserTaskKey()) {
      return false;
    }
    final Object this$assignee = getAssignee();
    final Object other$assignee = other.getAssignee();
    if (this$assignee == null ? other$assignee != null : !this$assignee.equals(other$assignee)) {
      return false;
    }
    final Object this$candidateGroupsList = getCandidateGroupsList();
    final Object other$candidateGroupsList = other.getCandidateGroupsList();
    if (this$candidateGroupsList == null
        ? other$candidateGroupsList != null
        : !this$candidateGroupsList.equals(other$candidateGroupsList)) {
      return false;
    }
    final Object this$candidateUsersList = getCandidateUsersList();
    final Object other$candidateUsersList = other.getCandidateUsersList();
    if (this$candidateUsersList == null
        ? other$candidateUsersList != null
        : !this$candidateUsersList.equals(other$candidateUsersList)) {
      return false;
    }
    final Object this$dueDate = getDueDate();
    final Object other$dueDate = other.getDueDate();
    if (this$dueDate == null ? other$dueDate != null : !this$dueDate.equals(other$dueDate)) {
      return false;
    }
    final Object this$elementId = getElementId();
    final Object other$elementId = other.getElementId();
    if (this$elementId == null
        ? other$elementId != null
        : !this$elementId.equals(other$elementId)) {
      return false;
    }
    if (getElementInstanceKey() != other.getElementInstanceKey()) {
      return false;
    }
    final Object this$bpmnProcessId = getBpmnProcessId();
    final Object other$bpmnProcessId = other.getBpmnProcessId();
    if (this$bpmnProcessId == null
        ? other$bpmnProcessId != null
        : !this$bpmnProcessId.equals(other$bpmnProcessId)) {
      return false;
    }
    if (getProcessDefinitionVersion() != other.getProcessDefinitionVersion()) {
      return false;
    }
    if (getProcessDefinitionKey() != other.getProcessDefinitionKey()) {
      return false;
    }
    if (getProcessInstanceKey() != other.getProcessInstanceKey()) {
      return false;
    }
    final Object this$tenantId = getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    final Object this$changedAttributes = getChangedAttributes();
    final Object other$changedAttributes = other.getChangedAttributes();
    if (this$changedAttributes == null
        ? other$changedAttributes != null
        : !this$changedAttributes.equals(other$changedAttributes)) {
      return false;
    }
    final Object this$variables = getVariables();
    final Object other$variables = other.getVariables();
    if (this$variables == null
        ? other$variables != null
        : !this$variables.equals(other$variables)) {
      return false;
    }
    final Object this$followUpDate = getFollowUpDate();
    final Object other$followUpDate = other.getFollowUpDate();
    if (this$followUpDate == null
        ? other$followUpDate != null
        : !this$followUpDate.equals(other$followUpDate)) {
      return false;
    }
    if (getFormKey() != other.getFormKey()) {
      return false;
    }
    final Object this$action = getAction();
    final Object other$action = other.getAction();
    if (this$action == null ? other$action != null : !this$action.equals(other$action)) {
      return false;
    }
    final Object this$externalFormReference = getExternalFormReference();
    final Object other$externalFormReference = other.getExternalFormReference();
    if (this$externalFormReference == null
        ? other$externalFormReference != null
        : !this$externalFormReference.equals(other$externalFormReference)) {
      return false;
    }
    final Object this$customHeaders = getCustomHeaders();
    final Object other$customHeaders = other.getCustomHeaders();
    if (this$customHeaders == null
        ? other$customHeaders != null
        : !this$customHeaders.equals(other$customHeaders)) {
      return false;
    }
    if (getCreationTimestamp() != other.getCreationTimestamp()) {
      return false;
    }
    return true;
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
        + ")";
  }

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
