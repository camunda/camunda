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
package io.camunda.operate.entities;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public class UserTaskEntity extends OperateZeebeEntity<UserTaskEntity> {

  private Long userTaskKey;
  private String assignee;
  private List<String> candidateGroups;
  private List<String> candidateUsers;
  private OffsetDateTime dueDate;
  private OffsetDateTime followUpDate;
  private Long formKey;
  private String elementId;
  private Long elementInstanceKey;
  private String bpmnProcessId;
  private Long processDefinitionKey;
  private Integer processDefinitionVersion;
  private Long processInstanceKey;
  private String variables;
  private String externalReference;
  private String action;
  private List<String> changedAttributes;
  private String tenantId = DEFAULT_TENANT_ID;

  public Long getUserTaskKey() {
    return userTaskKey;
  }

  public UserTaskEntity setUserTaskKey(Long userTaskKey) {
    this.userTaskKey = userTaskKey;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public UserTaskEntity setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public List<String> getCandidateGroups() {
    return candidateGroups;
  }

  public UserTaskEntity setCandidateGroups(List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public List<String> getCandidateUsers() {
    return candidateUsers;
  }

  public UserTaskEntity setCandidateUsers(List<String> candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public UserTaskEntity setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public UserTaskEntity setFollowUpDate(OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public Long getFormKey() {
    return formKey;
  }

  public UserTaskEntity setFormKey(Long formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getElementId() {
    return elementId;
  }

  public UserTaskEntity setElementId(String elementId) {
    this.elementId = elementId;
    return this;
  }

  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public UserTaskEntity setElementInstanceKey(Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public UserTaskEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public UserTaskEntity setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Integer getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public UserTaskEntity setProcessDefinitionVersion(Integer processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public UserTaskEntity setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getVariables() {
    return variables;
  }

  public UserTaskEntity setVariables(String variables) {
    this.variables = variables;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UserTaskEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getExternalReference() {
    return externalReference;
  }

  public UserTaskEntity setExternalReference(String externalReference) {
    this.externalReference = externalReference;
    return this;
  }

  public String getAction() {
    return action;
  }

  public UserTaskEntity setAction(String action) {
    this.action = action;
    return this;
  }

  public List<String> getChangedAttributes() {
    return changedAttributes;
  }

  public UserTaskEntity setChangedAttributes(List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    UserTaskEntity that = (UserTaskEntity) o;
    return Objects.equals(userTaskKey, that.userTaskKey)
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(candidateGroups, that.candidateGroups)
        && Objects.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(dueDate, that.dueDate)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(elementInstanceKey, that.elementInstanceKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processDefinitionVersion, that.processDefinitionVersion)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(variables, that.variables)
        && Objects.equals(externalReference, that.externalReference)
        && Objects.equals(action, that.action)
        && Objects.equals(changedAttributes, that.changedAttributes)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
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
        processDefinitionKey,
        processDefinitionVersion,
        processInstanceKey,
        variables,
        externalReference,
        action,
        changedAttributes,
        tenantId);
  }
}
