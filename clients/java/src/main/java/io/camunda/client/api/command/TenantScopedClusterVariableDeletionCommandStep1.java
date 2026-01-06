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

import io.camunda.client.api.response.DeleteClusterVariableResponse;

/**
 * Represents a request to delete a tenant-scoped cluster variable.
 *
 * <p>Usage example:
 *
 * <pre>
 *   camundaClient
 *       .newTenantScopedClusterVariableDeleteRequest("my-tenant-id")
 *       .delete("myVariable")
 *       .send()
 *       .join();
 * </pre>
 */
public interface TenantScopedClusterVariableDeletionCommandStep1
    extends FinalCommandStep<DeleteClusterVariableResponse> {

  /**
   * Specifies the name of the cluster variable to delete.
   *
   * <p>The variable will be deleted only if it exists with the specified name and scope. If the
   * variable does not exist, a 404 error will be returned.
   *
   * <pre>
   *   camundaClient
   *       .newTenantScopedClusterVariableDeleteRequest("my-tenant-id")
   *       .delete("myVariable")
   *       .send()
   *       .join();
   * </pre>
   *
   * @param name the name of the variable to delete. Must not be null or empty.
   * @return this builder for method chaining
   */
  TenantScopedClusterVariableDeletionCommandStep1 delete(String name);
}
