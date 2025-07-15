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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;

public interface TenantFilter extends SearchRequestFilter {
  /**
   * Filter tenants by the specified tenant ID.
   *
   * @param tenantId the ID of the tenant
   * @return the updated filter
   */
  TenantFilter tenantId(final String tenantId);

  /**
   * Filter tenants by the specified name.
   *
   * @param name the name of the tenant
   * @return the updated filter
   */
  TenantFilter name(final String name);
}
