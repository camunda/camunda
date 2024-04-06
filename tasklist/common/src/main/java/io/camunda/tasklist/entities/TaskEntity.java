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
package io.camunda.tasklist.entities;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class TaskEntity extends TasklistZeebeEntity<TaskEntity> {

  private String bpmnProcessId;
  private String processDefinitionId;
  private String flowNodeBpmnId;
  private String flowNodeInstanceId;
  private String processInstanceId;
  private OffsetDateTime creationTime;
  private OffsetDateTime completionTime;
  private TaskState state;
  private String assignee;
  private String[] candidateGroups;
  private String[] candidateUsers;
  private String formKey;
  private String formId;
  private Long formVersion;
  private Boolean isFormEmbedded;
  private OffsetDateTime followUpDate;
  private OffsetDateTime dueDate;
  private TaskImplementation implementation;

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskEntity setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public TaskEntity setFlowNodeBpmnId(String flowNodeBpmnId) {
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

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskEntity setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public TaskEntity setCreationTime(OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public OffsetDateTime getCompletionTime() {
    return completionTime;
  }

  public TaskEntity setCompletionTime(OffsetDateTime completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public TaskState getState() {
    return state;
  }

  public TaskEntity setState(TaskState state) {
    this.state = state;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskEntity setAssignee(String assignee) {
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

  public String getFormKey() {
    return formKey;
  }

  public TaskEntity setFormKey(final String formId) {
    this.formKey = formId;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TaskEntity setFormId(String formId) {
    this.formId = formId;
    return this;
  }

  public Long getFormVersion() {
    return formVersion;
  }

  public TaskEntity setFormVersion(Long formVersion) {
    this.formVersion = formVersion;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public TaskEntity setIsFormEmbedded(Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskEntity setFollowUpDate(OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskEntity setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskEntity setCandidateUsers(String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public TaskImplementation getImplementation() {
    return implementation;
  }

  public TaskEntity setImplementation(TaskImplementation implementation) {
    this.implementation = implementation;
    return this;
  }

  public TaskEntity makeCopy() {
    return new TaskEntity()
        .setId(this.getId())
        .setKey(this.getKey())
        .setPartitionId(this.getPartitionId())
        .setBpmnProcessId(this.getBpmnProcessId())
        .setProcessDefinitionId(this.getProcessDefinitionId())
        .setFlowNodeBpmnId(this.getFlowNodeBpmnId())
        .setFlowNodeInstanceId(this.getFlowNodeInstanceId())
        .setProcessInstanceId(this.getProcessInstanceId())
        .setCreationTime(this.getCreationTime())
        .setCompletionTime(this.getCompletionTime())
        .setState(this.getState())
        .setAssignee(this.getAssignee())
        .setCandidateGroups(this.getCandidateGroups())
        .setCandidateUsers(this.getCandidateUsers())
        .setFormKey(this.getFormKey())
        .setFormId(this.getFormId())
        .setFormVersion(this.getFormVersion())
        .setIsFormEmbedded(this.getIsFormEmbedded())
        .setTenantId(this.getTenantId())
        .setImplementation(this.getImplementation());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final TaskEntity that = (TaskEntity) o;
    return implementation == that.implementation
        && state == that.state
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(flowNodeBpmnId, that.flowNodeBpmnId)
        && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(creationTime, that.creationTime)
        && Objects.equals(completionTime, that.completionTime)
        && Objects.equals(assignee, that.assignee)
        && Arrays.equals(candidateGroups, that.candidateGroups)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(dueDate, that.dueDate)
        && Arrays.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(formId, that.formId)
        && Objects.equals(formVersion, that.formVersion)
        && Objects.equals(isFormEmbedded, that.isFormEmbedded);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            super.hashCode(),
            bpmnProcessId,
            processDefinitionId,
            flowNodeBpmnId,
            flowNodeInstanceId,
            processInstanceId,
            creationTime,
            completionTime,
            state,
            assignee,
            formKey,
            formId,
            formVersion,
            isFormEmbedded,
            followUpDate,
            dueDate,
            implementation);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    return result;
  }
}
