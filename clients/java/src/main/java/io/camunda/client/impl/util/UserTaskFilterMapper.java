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
package io.camunda.client.impl.util;

import io.camunda.client.protocol.rest.UserTaskFilter;
import io.camunda.client.protocol.rest.UserTaskFilterFields;

/** Utility class for mapping {@link UserTaskFilter} to {@link UserTaskFilterFields}. */
public final class UserTaskFilterMapper {

  private UserTaskFilterMapper() {
    // Prevent instantiation
  }

  public static UserTaskFilterFields from(final UserTaskFilter filter) {
    if (filter == null) {
      return null;
    }

    final UserTaskFilterFields target = new UserTaskFilterFields();

    target.setState(filter.getState());
    target.setAssignee(filter.getAssignee());
    target.setBusinessId(filter.getBusinessId());
    target.setPriority(filter.getPriority());
    target.setElementId(filter.getElementId());
    target.setName(filter.getName());
    target.setCandidateGroup(filter.getCandidateGroup());
    target.setCandidateUser(filter.getCandidateUser());
    target.setTenantId(filter.getTenantId());
    target.setProcessDefinitionId(filter.getProcessDefinitionId());
    target.setCreationDate(filter.getCreationDate());
    target.setCompletionDate(filter.getCompletionDate());
    target.setFollowUpDate(filter.getFollowUpDate());
    target.setDueDate(filter.getDueDate());
    target.setProcessInstanceVariables(filter.getProcessInstanceVariables());
    target.setLocalVariables(filter.getLocalVariables());
    target.setUserTaskKey(filter.getUserTaskKey());
    target.setProcessDefinitionKey(filter.getProcessDefinitionKey());
    target.setProcessInstanceKey(filter.getProcessInstanceKey());
    target.setElementInstanceKey(filter.getElementInstanceKey());
    target.setTags(filter.getTags());

    return target;
  }
}
