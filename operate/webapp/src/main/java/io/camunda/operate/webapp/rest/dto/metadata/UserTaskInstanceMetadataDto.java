/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.webapp.rest.dto.metadata;

import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.FlowNodeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class UserTaskInstanceMetadataDto extends FlowNodeInstanceMetadataDto
    implements FlowNodeInstanceMetadata {
  private OffsetDateTime dueDate;
  private OffsetDateTime followUpDate;
  private Long formKey;
  private String action;
  private List<String> changedAttributes;
  private String assignee;
  private Long userTaskKey;
  private Map<String, Object> variables = Map.of();
  private List<String> candidateGroups = List.of();
  private List<String> candidateUsers = List.of();
  private String tenantId;
  private String externalReference;

  public UserTaskInstanceMetadataDto(
      final String flowNodeId,
      final String flowNodeInstanceId,
      final FlowNodeType flowNodeType,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final EventEntity event) {
    super(flowNodeId, flowNodeInstanceId, flowNodeType, startDate, endDate, event);
  }

  public String getExternalReference() {
    return externalReference;
  }

  public UserTaskInstanceMetadataDto setExternalReference(final String externalReference) {
    this.externalReference = externalReference;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UserTaskInstanceMetadataDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public UserTaskInstanceMetadataDto setDueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public UserTaskInstanceMetadataDto setFollowUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public Long getFormKey() {
    return formKey;
  }

  public UserTaskInstanceMetadataDto setFormKey(final Long formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getAction() {
    return action;
  }

  public UserTaskInstanceMetadataDto setAction(final String action) {
    this.action = action;
    return this;
  }

  public List<String> getChangedAttributes() {
    return changedAttributes;
  }

  public UserTaskInstanceMetadataDto setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes = changedAttributes;
    return this;
  }

  public List<String> getCandidateGroups() {
    return candidateGroups;
  }

  public UserTaskInstanceMetadataDto setCandidateGroups(final List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public List<String> getCandidateUsers() {
    return candidateUsers;
  }

  public UserTaskInstanceMetadataDto setCandidateUsers(final List<String> candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public UserTaskInstanceMetadataDto setAssignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  public Long getUserTaskKey() {
    return userTaskKey;
  }

  public UserTaskInstanceMetadataDto setUserTaskKey(final Long userTaskKey) {
    this.userTaskKey = userTaskKey;
    return this;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  public UserTaskInstanceMetadataDto setVariables(final Map<String, Object> variables) {
    this.variables = variables;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        dueDate,
        followUpDate,
        formKey,
        action,
        changedAttributes,
        assignee,
        userTaskKey,
        variables,
        candidateGroups,
        candidateUsers,
        tenantId,
        externalReference);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    final UserTaskInstanceMetadataDto that = (UserTaskInstanceMetadataDto) o;
    return Objects.equals(dueDate, that.dueDate)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(action, that.action)
        && Objects.equals(changedAttributes, that.changedAttributes)
        && Objects.equals(assignee, that.assignee)
        && Objects.equals(userTaskKey, that.userTaskKey)
        && Objects.equals(variables, that.variables)
        && Objects.equals(candidateGroups, that.candidateGroups)
        && Objects.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(externalReference, that.externalReference);
  }

  @Override
  public String toString() {
    return "UserTaskInstanceMetadataDto{"
        + "dueDate="
        + dueDate
        + ", followUpDate="
        + followUpDate
        + ", formKey="
        + formKey
        + ", action='"
        + action
        + '\''
        + ", changedAttributes="
        + changedAttributes
        + ", assignee='"
        + assignee
        + '\''
        + ", userTaskKey="
        + userTaskKey
        + ", variables="
        + variables
        + ", candidateGroups="
        + candidateGroups
        + ", candidateUsers="
        + candidateUsers
        + ", tenantId='"
        + tenantId
        + '\''
        + ", externalReference='"
        + externalReference
        + '\''
        + "} "
        + super.toString();
  }
}
