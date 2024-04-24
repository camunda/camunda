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
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.entities.TaskFilterEntity;
import java.util.List;
import java.util.Objects;

public class AddFilterRequest {

  private String name;
  private String filter;
  private String createdBy;
  private List<String> candidateUsers;
  private List<String> candidateGroups;

  public TaskFilterEntity toFilterEntity() {
    TaskFilterEntity filterEntity = new TaskFilterEntity();
    filterEntity.setName(this.getName());
    filterEntity.setFilter(this.getFilter());
    filterEntity.setSharedGroups(this.getCandidateGroups());
    filterEntity.setSharedUsers(this.getCandidateUsers());
    filterEntity.setCreatedBy(this.getCreatedBy());
    return filterEntity;
  }

  public void validate() {
    if (this.getName() == null || this.getName().isBlank()) {
      throw new IllegalArgumentException("Name is mandatory");
    }
    if (this.getFilter() == null || this.getFilter().isEmpty()) {
      throw new IllegalArgumentException("Filter is mandatory");
    }
    if (this.getCreatedBy() == null || this.getCreatedBy().isEmpty()) {
      throw new IllegalArgumentException("CreatedBy is mandatory");
    }
  }

  public List<String> getCandidateGroups() {
    return candidateGroups;
  }

  public void setCandidateGroups(final List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getFilter() {
    return filter;
  }

  public void setFilter(final String filter) {
    this.filter = filter;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(final String user) {
    this.createdBy = user;
  }

  public List<String> getCandidateUsers() {
    return candidateUsers;
  }

  public void setCandidateUsers(final List<String> candidateUsers) {
    this.candidateUsers = candidateUsers;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AddFilterRequest that = (AddFilterRequest) o;
    return Objects.equals(name, that.name)
        && Objects.equals(filter, that.filter)
        && Objects.equals(createdBy, that.createdBy)
        && Objects.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(candidateGroups, that.candidateGroups);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, filter, createdBy, candidateUsers, candidateGroups);
  }
}
