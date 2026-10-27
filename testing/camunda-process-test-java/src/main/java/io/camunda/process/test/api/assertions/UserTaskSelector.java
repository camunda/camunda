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

import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.response.UserTask;

/** A selector for BPMN user task elements. */
@FunctionalInterface
public interface UserTaskSelector {

  /**
   * Checks if the element matches the selector.
   *
   * @param userTask the BPMN element
   * @return {@code true} if the element matches, otherwise {@code false}
   */
  boolean test(UserTask userTask);

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
   * Applies the given filter to limit the search of user task that match the selector. Default: no
   * filtering.
   *
   * @param filter the filter used to limit the user task search
   */
  default void applyFilter(final UserTaskFilter filter) {}

  /**
   * Combines two user task selectors together.
   *
   * @param other user task selector to be added.
   * @return the combined user task selector
   */
  default UserTaskSelector and(UserTaskSelector other) {
    final UserTaskSelector self = this;

    return new UserTaskSelector() {
      @Override
      public boolean test(final UserTask userTask) {
        return self.test(userTask) && other.test(userTask);
      }

      @Override
      public String describe() {
        return String.format("%s, %s", self.describe(), other.describe());
      }

      @Override
      public void applyFilter(final UserTaskFilter filter) {
        self.applyFilter(filter);
        other.applyFilter(filter);
      }
    };
  }
}
