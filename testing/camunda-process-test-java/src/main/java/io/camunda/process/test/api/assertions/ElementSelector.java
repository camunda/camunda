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
package io.camunda.process.test.api.assertions;

import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.api.search.response.FlowNodeInstance;

/** A selector for BPMN elements. */
@FunctionalInterface
public interface ElementSelector {

  /**
   * Checks if the element matches the selector.
   *
   * @param element the BPMN element
   * @return {@code true} if the element matches, otherwise {@code false}
   */
  boolean test(FlowNodeInstance element);

  /**
   * Returns a string representation of the selector. It is used to build the failure message of an
   * assertion. Default: {@link Object#toString()}.
   *
   * @return the string representation
   */
  default String describe() {
    return toString();
  }

  /**
   * Applies the given filter to limit the search of element instances that match the selector.
   * Default: no filtering.
   *
   * @param filter the filter used to limit the element instance search
   */
  default void applyFilter(final FlownodeInstanceFilter filter) {}
}
