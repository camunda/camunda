/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.zeebeimport.v860.record.value;

import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class UserTaskRecordValueImpl implements UserTaskRecordValue {
  private long userTaskKey;
  private String assignee;
  private String candidateGroups;
  private List<String> candidateGroupsList;
  private String candidateUsers;
  private List<String> candidateUsersList;
  private String dueDate;

  private String followUpDate;

  private long formKey;

  private String elementId;

  private long elementInstanceKey;

  private String bpmnProcessId;

  private int processDefinitionVersion;

  private long processDefinitionKey;

  private Map<String, Object> variables;

  private String tenantId;

  private long processInstanceKey;

  private List<String> changedAttributes;
  private String action;
  private String externalFormReference;
  private Map<String, String> customHeaders;
  private long creationTimestamp;

  @Override
  public long getUserTaskKey() {
    return userTaskKey;
  }

  public void setUserTaskKey(final long userTaskKey) {
    this.userTaskKey = userTaskKey;
  }

  @Override
  public String getAssignee() {
    return assignee;
  }

  public void setAssignee(final String assignee) {
    this.assignee = assignee;
  }

  @Override
  public List<String> getCandidateGroupsList() {
    return candidateGroupsList;
  }

  public void setCandidateGroupsList(final List<String> candidateGroupsList) {
    this.candidateGroupsList = candidateGroupsList;
  }

  @Override
  public List<String> getCandidateUsersList() {
    return candidateUsersList;
  }

  @Override
  public String getDueDate() {
    return dueDate;
  }

  public void setDueDate(final String dueDate) {
    this.dueDate = dueDate;
  }

  @Override
  public String getFollowUpDate() {
    return followUpDate;
  }

  public void setFollowUpDate(final String followUpDate) {
    this.followUpDate = followUpDate;
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

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
  }

  public void setFormKey(final long formKey) {
    this.formKey = formKey;
  }

  public void setCandidateUsersList(final List<String> candidateUsersList) {
    this.candidateUsersList = candidateUsersList;
  }

  public String getCandidateGroups() {
    return candidateGroups;
  }

  public void setCandidateGroups(final String candidateGroups) {
    this.candidateGroups = candidateGroups;
  }

  public String getCandidateUsers() {
    return candidateUsers;
  }

  public void setCandidateUsers(final String candidateUsers) {
    this.candidateUsers = candidateUsers;
  }

  @Override
  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(final Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        userTaskKey,
        assignee,
        candidateGroups,
        candidateUsers,
        dueDate,
        followUpDate,
        formKey,
        elementId,
        elementInstanceKey,
        bpmnProcessId,
        processDefinitionVersion,
        processDefinitionKey,
        variables,
        tenantId,
        processInstanceKey,
        changedAttributes);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UserTaskRecordValueImpl that = (UserTaskRecordValueImpl) o;
    return userTaskKey == that.userTaskKey
        && formKey == that.formKey
        && elementInstanceKey == that.elementInstanceKey
        && processDefinitionVersion == that.processDefinitionVersion
        && processDefinitionKey == that.processDefinitionKey
        && processInstanceKey == that.processInstanceKey
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(candidateGroups, that.candidateGroups)
        && Objects.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(variables, that.variables)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(changedAttributes, that.changedAttributes);
  }

  @Override
  public String toString() {
    return "UserTaskRecordValueImpl{"
        + "userTaskKey="
        + userTaskKey
        + ", assignee='"
        + assignee
        + '\''
        + ", candidateGroups='"
        + candidateGroups
        + '\''
        + ", candidateUsers='"
        + candidateUsers
        + '\''
        + ", dueDate='"
        + dueDate
        + '\''
        + ", followUpDate='"
        + followUpDate
        + '\''
        + ", formKey="
        + formKey
        + ", elementId='"
        + elementId
        + '\''
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", processDefinitionVersion="
        + processDefinitionVersion
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", variables="
        + variables
        + ", tenantId='"
        + tenantId
        + '\''
        + ", processInstanceKey="
        + processInstanceKey
        + ", changedAttributes="
        + changedAttributes
        + '}';
  }
}
