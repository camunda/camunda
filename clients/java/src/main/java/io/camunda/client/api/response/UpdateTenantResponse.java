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
package io.camunda.client.api.response;

public interface UpdateTenantResponse {
  /**
   * Returns the unique identifier (ID) of the updated tenant.
   *
   * <p>The tenant ID is a user-defined identifier for the tenant. It is specified when the tenant
   * is created and is often used for human-readable identification or external references.
   *
   * @return the user-defined tenant ID.
   */
  String getTenantId();

  /**
   * Returns the name of the updated tenant.
   *
   * @return the tenant name.
   */
  String getName();

  /** Returns the description of the updated tenant. */
  String getDescription();
}
