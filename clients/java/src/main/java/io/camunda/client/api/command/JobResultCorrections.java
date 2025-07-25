/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.api.command;

import java.time.OffsetDateTime;
import java.util.List;

public class JobResultCorrections {

  private String assignee;
  private OffsetDateTime dueDate;
  private OffsetDateTime followUpDate;
  private List<String> candidateGroups;
  private List<String> candidateUsers;
  private Integer priority;

  /**
   * Correct the assignee of the task.
   *
   * @param assignee assignee of the task
   * @return this corrections
   */
  public JobResultCorrections assignee(final String assignee) {
    this.assignee = assignee;
    return this;
  }

  /**
   * Correct the due date of the task.
   *
   * @param dueDate due date of the task
   * @return this corrections
   */
  public JobResultCorrections dueDate(final OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  /**
   * Correct the follow up date of the task.
   *
   * @param followUpDate follow up date of the task
   * @return this corrections
   */
  public JobResultCorrections followUpDate(final OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  /**
   * Correct the candidate groups of the task.
   *
   * @param candidateGroups candidate groups of the task
   * @return this corrections
   */
  public JobResultCorrections candidateGroups(final List<String> candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  /**
   * Correct the candidate users of the task.
   *
   * @param candidateUsers candidate users of the task
   * @return this corrections
   */
  public JobResultCorrections candidateUsers(final List<String> candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  /**
   * Correct the priority of the task.
   *
   * @param priority priority of the task
   * @return this corrections
   */
  public JobResultCorrections priority(final Integer priority) {
    this.priority = priority;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public List<String> getCandidateGroups() {
    return candidateGroups;
  }

  public List<String> getCandidateUsers() {
    return candidateUsers;
  }

  public Integer getPriority() {
    return priority;
  }
}
