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

import io.camunda.client.api.response.UnassignMappingRuleFromRoleResponse;

public interface UnassignMappingRuleFromRoleCommandStep1 {

  /**
   * Sets the role ID.
   *
   * @param roleId the roleId of the role
   * @return the builder for this command.
   */
  UnassignMappingRuleFromRoleCommandStep2 roleId(String roleId);

  interface UnassignMappingRuleFromRoleCommandStep2
      extends FinalCommandStep<UnassignMappingRuleFromRoleResponse> {

    /**
     * Sets the mapping rule ID.
     *
     * @param mappingRuleId the mappingRuleId of the mapping rule
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    UnassignMappingRuleFromRoleCommandStep2 mappingRuleId(String mappingRuleId);
  }
}
