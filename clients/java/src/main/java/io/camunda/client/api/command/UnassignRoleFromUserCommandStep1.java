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

import io.camunda.client.api.response.UnassignUserFromRoleResponse;

public interface UnassignRoleFromUserCommandStep1 {

  /**
   * Sets the role ID for the unassignment.
   *
   * @param roleId the roleId of the role
   * @return the next step of the command builder.
   */
  UnassignRoleFromUserCommandStep2 roleId(String roleId);

  interface UnassignRoleFromUserCommandStep2 {

    /**
     * Sets the username for the unassignment.
     *
     * @param username the username of the user
     * @return the builder for this command. Call {@link UnassignRoleFromUserCommandStep3#send()} to
     *     complete the command and send it to the broker.
     */
    UnassignRoleFromUserCommandStep3 username(String username);
  }

  interface UnassignRoleFromUserCommandStep3
      extends FinalCommandStep<UnassignUserFromRoleResponse> {}
}
