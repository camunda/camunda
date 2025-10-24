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

import io.camunda.client.api.response.UnassignUserFromTenantResponse;

/** Command to unassign a user from a tenant. */
public interface UnassignUserFromTenantCommandStep1 {

  /**
   * Sets the username for unassignment.
   *
   * @param username the username of the user
   * @return the builder for this command.
   */
  UnassignUserFromTenantCommandStep2 username(String username);

  interface UnassignUserFromTenantCommandStep2 {

    /**
     * Sets the tenant ID.
     *
     * @param tenantId the tenantId of the tenant
     * @return the builder for this command. Call {@link UnassignUserFromTenantCommandStep3#send()}
     *     to complete the command and send it to the broker.
     */
    UnassignUserFromTenantCommandStep3 tenantId(String tenantId);
  }

  interface UnassignUserFromTenantCommandStep3
      extends FinalCommandStep<UnassignUserFromTenantResponse> {}
}
