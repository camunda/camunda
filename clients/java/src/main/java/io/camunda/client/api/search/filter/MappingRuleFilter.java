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

import io.camunda.client.api.search.filter.builder.StringProperty;
import java.util.List;
import java.util.function.Consumer;

public interface MappingRuleFilter extends MappingRuleFilterBase {

  /**
   * Filter mapping rules by the specified mapping rule id.
   *
   * @param mappingRuleId the id of the mapping rule
   * @return the updated filter
   */
  @Override
  MappingRuleFilter mappingRuleId(final String mappingRuleId);

  @Override
  MappingRuleFilter mappingRuleId(Consumer<StringProperty> fn);

  /**
   * Filter mapping rules by the specified claim name.
   *
   * @param claimName the name of the claim
   * @return the updated filter
   */
  @Override
  MappingRuleFilter claimName(final String claimName);

  /**
   * Filter mapping rules by the specified claim value.
   *
   * @param claimValue the value of the claim
   * @return the updated filter
   */
  @Override
  MappingRuleFilter claimValue(final String claimValue);

  /**
   * Filter mapping rules by the specified name.
   *
   * @param name the name of the mapping rule
   * @return the updated filter
   */
  @Override
  MappingRuleFilter name(final String name);

  @Override
  MappingRuleFilter name(Consumer<StringProperty> fn);

  /**
   * Combine this filter with a list of alternative filter groups using OR logic.
   *
   * @param filters the alternative filter groups
   * @return the updated filter
   */
  MappingRuleFilterBase orFilters(List<Consumer<MappingRuleFilterBase>> filters);
}
