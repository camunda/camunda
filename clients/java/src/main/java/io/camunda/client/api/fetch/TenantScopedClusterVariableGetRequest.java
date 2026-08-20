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
package io.camunda.client.api.fetch;

import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.search.response.ClusterVariable;

/**
 * Represents a request to fetch a tenant-scoped cluster variable by name.
 *
 * <p>Usage example:
 *
 * <pre>
 *   ClusterVariable response = camundaClient
 *       .newTenantScopedClusterVariableGetRequest("my-tenant-id")
 *       .withName("myVariable")
 *       .send()
 *       .join();
 * </pre>
 */
public interface TenantScopedClusterVariableGetRequest extends FinalCommandStep<ClusterVariable> {

  /**
   * Specifies the name of the cluster variable to fetch.
   *
   * <p>The variable will be fetched only if it exists with the specified name and tenant ID. If the
   * variable does not exist, a 404 error will be returned.
   *
   * @param name the name of the variable to fetch. Must not be null or empty.
   * @return this builder for method chaining
   */
  TenantScopedClusterVariableGetRequest withName(String name);
}
