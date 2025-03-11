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

import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.ProcessInstance;

/** A selector for process instances. */
@FunctionalInterface
public interface ProcessInstanceSelector {

  /**
   * Checks if a process instance matches the selector.
   *
   * @param processInstance the process instance
   * @return {@code true} if the process instance matches, otherwise {@code false}
   */
  boolean test(ProcessInstance processInstance);

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
   * Applies the given filter to limit the search of process instances that match the selector.
   * Default: no filtering.
   *
   * @param filter the filter used to limit the process instance search
   */
  default void applyFilter(final ProcessInstanceFilter filter) {}
}
