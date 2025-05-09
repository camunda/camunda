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

import io.camunda.client.api.response.CreateRoleResponse;

public interface CreateRoleCommandStep1 {
  /**
   * Set the ID to create role with.
   *
   * @param roleId the role ID
   * @return the builder for this command.
   */
  CreateRoleCommandStep2 roleId(String roleId);

  interface CreateRoleCommandStep2 extends FinalCommandStep<CreateRoleResponse> {
    /**
     * Set the name for the role to be created.
     *
     * @param name the role name
     * @return the builder for this command
     */
    CreateRoleCommandStep2 name(String name);

    /**
     * Set the description for the role to be created.
     *
     * @param description the role description
     * @return the builder for this command
     */
    CreateRoleCommandStep2 description(String description);
  }
}
