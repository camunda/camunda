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

import io.camunda.client.api.response.AssignRoleToUserResponse;

/**
 * Command to assign a role to a user.
 *
 * <p>Example usage:
 *
 * <pre>
 * camundaClient
 *   .newAssignRoleToUserCommand()
 *   .roleId("roleId")
 *   .username("username")
 *   .send();
 * </pre>
 *
 * <p>This command is only sent via REST over HTTP, not via gRPC.
 */
public interface AssignRoleToUserCommandStep1 {

  /**
   * Sets the role ID.
   *
   * @param roleId the ID of the role
   * @return the next step of the builder to set the username
   */
  AssignRoleToUserCommandStep2 roleId(String roleId);

  interface AssignRoleToUserCommandStep2 {

    /**
     * Sets the username of the user to assign the role to.
     *
     * @param username the username
     * @return the builder for this command. Call {@link AssignRoleToUserCommandStep3#send()} to
     *     execute.
     */
    AssignRoleToUserCommandStep3 username(String username);
  }

  interface AssignRoleToUserCommandStep3 extends FinalCommandStep<AssignRoleToUserResponse> {}
}
