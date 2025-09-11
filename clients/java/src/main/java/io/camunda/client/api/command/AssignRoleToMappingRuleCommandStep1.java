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

import io.camunda.client.api.response.AssignRoleToMappingRuleResponse;

/** Command to assign mapping rule to a role. */
public interface AssignRoleToMappingRuleCommandStep1 {

  /**
   * Sets the role ID.
   *
   * @param roleId the id of the role
   * @return the builder for this command
   */
  AssignRoleToMappingRuleCommandStep2 roleId(String roleId);

  interface AssignRoleToMappingRuleCommandStep2 {
    /**
     * Sets the mapping rule ID.
     *
     * @param mappingRuleId the id of the mapping rule
     * @return the builder for this command. Call {@link AssignRoleToMappingRuleCommandStep3#send()}
     *     to complete the command and send it to the broker.
     */
    AssignRoleToMappingRuleCommandStep3 mappingRuleId(String mappingRuleId);
  }

  interface AssignRoleToMappingRuleCommandStep3
      extends FinalCommandStep<AssignRoleToMappingRuleResponse> {}
}
