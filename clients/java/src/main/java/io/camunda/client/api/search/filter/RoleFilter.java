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

import io.camunda.client.api.search.request.TypedSearchRequest.SearchRequestFilter;

public interface RoleFilter extends SearchRequestFilter {

  /**
   * Filter roles by the specified role ID.
   *
   * @param roleId the role ID
   * @return the updated filter
   */
  RoleFilter roleId(final String roleId);

  /**
   * Filter roles by name.
   *
   * @param name the role name
   * @return the updated filter
   */
  RoleFilter name(final String name);
}
