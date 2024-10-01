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

  public void setUserTaskKey(long userTaskKey) {
    this.userTaskKey = userTaskKey;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }

  public void setCandidateGroupsList(List<String> candidateGroupsList) {
    this.candidateGroupsList = candidateGroupsList;
  }

  public void setCandidateUsersList(List<String> candidateUsersList) {
    this.candidateUsersList = candidateUsersList;
  }

  public void setDueDate(String dueDate) {
    this.dueDate = dueDate;
  }

  public void setElementId(String elementId) {
    this.elementId = elementId;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setProcessDefinitionVersion(int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public void setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setProcessInstanceKey(long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public void setChangedAttributes(List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
  }

  public void setVariables(Map<String, Object> variables) {
    this.variables = variables;
  }

  public void setFollowUpDate(String followUpDate) {
    this.followUpDate = followUpDate;
  }

  public void setFormKey(long formKey) {
    this.formKey = formKey;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public void setExternalFormReference(String externalFormReference) {
    this.externalFormReference = externalFormReference;
  }

  public void setCustomHeaders(Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
  }

  public void setCreationTimestamp(long creationTimestamp) {
    this.creationTimestamp = creationTimestamp;
  }

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
    if (this.getUserTaskKey() != other.getUserTaskKey()) {
      return false;
    }
    final Object this$assignee = this.getAssignee();
    final Object other$assignee = other.getAssignee();
    if (this$assignee == null ? other$assignee != null : !this$assignee.equals(other$assignee)) {
      return false;
    }
    final Object this$candidateGroupsList = this.getCandidateGroupsList();
    final Object other$candidateGroupsList = other.getCandidateGroupsList();
    if (this$candidateGroupsList == null
        ? other$candidateGroupsList != null
        : !this$candidateGroupsList.equals(other$candidateGroupsList)) {
      return false;
    }
    final Object this$candidateUsersList = this.getCandidateUsersList();
    final Object other$candidateUsersList = other.getCandidateUsersList();
    if (this$candidateUsersList == null
        ? other$candidateUsersList != null
        : !this$candidateUsersList.equals(other$candidateUsersList)) {
      return false;
    }
    final Object this$dueDate = this.getDueDate();
    final Object other$dueDate = other.getDueDate();
    if (this$dueDate == null ? other$dueDate != null : !this$dueDate.equals(other$dueDate)) {
      return false;
    }
    final Object this$elementId = this.getElementId();
    final Object other$elementId = other.getElementId();
    if (this$elementId == null
        ? other$elementId != null
        : !this$elementId.equals(other$elementId)) {
      return false;
    }
    if (this.getElementInstanceKey() != other.getElementInstanceKey()) {
      return false;
    }
    final Object this$bpmnProcessId = this.getBpmnProcessId();
    final Object other$bpmnProcessId = other.getBpmnProcessId();
    if (this$bpmnProcessId == null
        ? other$bpmnProcessId != null
        : !this$bpmnProcessId.equals(other$bpmnProcessId)) {
      return false;
    }
    if (this.getProcessDefinitionVersion() != other.getProcessDefinitionVersion()) {
      return false;
    }
    if (this.getProcessDefinitionKey() != other.getProcessDefinitionKey()) {
      return false;
    }
    if (this.getProcessInstanceKey() != other.getProcessInstanceKey()) {
      return false;
    }
    final Object this$tenantId = this.getTenantId();
    final Object other$tenantId = other.getTenantId();
    if (this$tenantId == null ? other$tenantId != null : !this$tenantId.equals(other$tenantId)) {
      return false;
    }
    final Object this$changedAttributes = this.getChangedAttributes();
    final Object other$changedAttributes = other.getChangedAttributes();
    if (this$changedAttributes == null
        ? other$changedAttributes != null
        : !this$changedAttributes.equals(other$changedAttributes)) {
      return false;
    }
    final Object this$variables = this.getVariables();
    final Object other$variables = other.getVariables();
    if (this$variables == null
        ? other$variables != null
        : !this$variables.equals(other$variables)) {
      return false;
    }
    final Object this$followUpDate = this.getFollowUpDate();
    final Object other$followUpDate = other.getFollowUpDate();
    if (this$followUpDate == null
        ? other$followUpDate != null
        : !this$followUpDate.equals(other$followUpDate)) {
      return false;
    }
    if (this.getFormKey() != other.getFormKey()) {
      return false;
    }
    final Object this$action = this.getAction();
    final Object other$action = other.getAction();
    if (this$action == null ? other$action != null : !this$action.equals(other$action)) {
      return false;
    }
    final Object this$externalFormReference = this.getExternalFormReference();
    final Object other$externalFormReference = other.getExternalFormReference();
    if (this$externalFormReference == null
        ? other$externalFormReference != null
        : !this$externalFormReference.equals(other$externalFormReference)) {
      return false;
    }
    final Object this$customHeaders = this.getCustomHeaders();
    final Object other$customHeaders = other.getCustomHeaders();
    if (this$customHeaders == null
        ? other$customHeaders != null
        : !this$customHeaders.equals(other$customHeaders)) {
      return false;
    }
    if (this.getCreationTimestamp() != other.getCreationTimestamp()) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeUserTaskDataDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final long $userTaskKey = this.getUserTaskKey();
    result = result * PRIME + (int) ($userTaskKey >>> 32 ^ $userTaskKey);
    final Object $assignee = this.getAssignee();
    result = result * PRIME + ($assignee == null ? 43 : $assignee.hashCode());
    final Object $candidateGroupsList = this.getCandidateGroupsList();
    result = result * PRIME + ($candidateGroupsList == null ? 43 : $candidateGroupsList.hashCode());
    final Object $candidateUsersList = this.getCandidateUsersList();
    result = result * PRIME + ($candidateUsersList == null ? 43 : $candidateUsersList.hashCode());
    final Object $dueDate = this.getDueDate();
    result = result * PRIME + ($dueDate == null ? 43 : $dueDate.hashCode());
    final Object $elementId = this.getElementId();
    result = result * PRIME + ($elementId == null ? 43 : $elementId.hashCode());
    final long $elementInstanceKey = this.getElementInstanceKey();
    result = result * PRIME + (int) ($elementInstanceKey >>> 32 ^ $elementInstanceKey);
    final Object $bpmnProcessId = this.getBpmnProcessId();
    result = result * PRIME + ($bpmnProcessId == null ? 43 : $bpmnProcessId.hashCode());
    result = result * PRIME + this.getProcessDefinitionVersion();
    final long $processDefinitionKey = this.getProcessDefinitionKey();
    result = result * PRIME + (int) ($processDefinitionKey >>> 32 ^ $processDefinitionKey);
    final long $processInstanceKey = this.getProcessInstanceKey();
    result = result * PRIME + (int) ($processInstanceKey >>> 32 ^ $processInstanceKey);
    final Object $tenantId = this.getTenantId();
    result = result * PRIME + ($tenantId == null ? 43 : $tenantId.hashCode());
    final Object $changedAttributes = this.getChangedAttributes();
    result = result * PRIME + ($changedAttributes == null ? 43 : $changedAttributes.hashCode());
    final Object $variables = this.getVariables();
    result = result * PRIME + ($variables == null ? 43 : $variables.hashCode());
    final Object $followUpDate = this.getFollowUpDate();
    result = result * PRIME + ($followUpDate == null ? 43 : $followUpDate.hashCode());
    final long $formKey = this.getFormKey();
    result = result * PRIME + (int) ($formKey >>> 32 ^ $formKey);
    final Object $action = this.getAction();
    result = result * PRIME + ($action == null ? 43 : $action.hashCode());
    final Object $externalFormReference = this.getExternalFormReference();
    result =
        result * PRIME + ($externalFormReference == null ? 43 : $externalFormReference.hashCode());
    final Object $customHeaders = this.getCustomHeaders();
    result = result * PRIME + ($customHeaders == null ? 43 : $customHeaders.hashCode());
    final long $creationTimestamp = this.getCreationTimestamp();
    result = result * PRIME + (int) ($creationTimestamp >>> 32 ^ $creationTimestamp);
    return result;
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
