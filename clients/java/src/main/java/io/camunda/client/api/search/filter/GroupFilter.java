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

public interface GroupFilter extends GroupFilterBase {

  /**
   * Filters groups by the specified groupId.
   *
   * @param groupId the ID of the group
   * @return the updated filter
   */
  @Override
  GroupFilter groupId(final String groupId);

  @Override
  GroupFilter groupId(Consumer<StringProperty> fn);

  /**
   * Filters groups by the specified name.
   *
   * @param name the name of the group
   * @return the updated filter
   */
  @Override
  GroupFilter name(final String name);

  @Override
  GroupFilter name(Consumer<StringProperty> fn);

  /**
   * Combine this filter with a list of alternative filter groups using OR logic.
   *
   * @param filters the alternative filter groups
   * @return the updated filter
   */
  GroupFilterBase orFilters(List<Consumer<GroupFilterBase>> filters);
}
