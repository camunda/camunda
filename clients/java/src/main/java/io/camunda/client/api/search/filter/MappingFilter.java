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

public interface MappingFilter extends SearchRequestFilter {

  /**
   * Filter mappings by the specified mapping id.
   *
   * @param mappingId the id of the mapping
   * @return the updated filter
   */
  MappingFilter mappingRuleId(final String mappingId);

  /**
   * Filter mappings by the specified claim name.
   *
   * @param claimName the name of the claim
   * @return the updated filter
   */
  MappingFilter claimName(final String claimName);

  /**
   * Filter mappings by the specified claim value.
   *
   * @param claimValue the value of the claim
   * @return the updated filter
   */
  MappingFilter claimValue(final String claimValue);

  /**
   * Filter mappings by the specified name.
   *
   * @param name the name of the mapping
   * @return the updated filter
   */
  MappingFilter name(final String name);
}
