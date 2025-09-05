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

import io.camunda.client.api.response.AssignMappingRuleToGroupResponse;

public interface AssignMappingRuleToGroupStep1 {

  /**
   * Sets the mapping rule ID for the assignment.
   *
   * @param mappingRuleId the ID of the mapping rule to add
   * @return the builder for this command.
   */
  AssignMappingRuleToGroupStep2 mappingRuleId(String mappingRuleId);

  interface AssignMappingRuleToGroupStep2 {

    /**
     * Sets the group ID.
     *
     * @param groupId the groupId of the group
     * @return the builder for this command. Call {@link AssignMappingRuleToGroupStep3#send()} to
     *     complete the command and send it to the broker.
     */
    AssignMappingRuleToGroupStep3 groupId(String groupId);
  }

  interface AssignMappingRuleToGroupStep3
      extends FinalCommandStep<AssignMappingRuleToGroupResponse> {}
}
