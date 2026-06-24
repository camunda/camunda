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

import io.camunda.client.api.response.UpdateClusterVariableResponse;

/**
 * Represents a request to update a tenant-scoped cluster variable.
 *
 * <p>Usage example:
 *
 * <pre>
 *   UpdateClusterVariableResponse response = camundaClient
 *       .newTenantScopedClusterVariableUpdateRequest("my-tenant-id")
 *       .update("myVariable", "newValue")
 *       .send()
 *       .join();
 * </pre>
 */
public interface TenantScopedClusterVariableUpdateCommandStep1
    extends FinalCommandStep<UpdateClusterVariableResponse> {

  /**
   * Sets the name and new value of the cluster variable to update.
   *
   * <p>The variable value will be serialized to JSON format.
   *
   * <pre>
   *   camundaClient
   *       .newTenantScopedClusterVariableUpdateRequest("my-tenant-id")
   *       .update("myVariable", "newValue")  // for string values
   *       .send();
   * </pre>
   *
   * @param name the name of the variable. Must not be null or empty.
   * @param value the new value of the variable. Must not be null. Will be serialized to JSON.
   * @return this builder for method chaining
   */
  TenantScopedClusterVariableUpdateCommandStep1 update(String name, Object value);
}
