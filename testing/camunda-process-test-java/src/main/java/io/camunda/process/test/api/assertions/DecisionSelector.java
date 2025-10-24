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

import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.response.DecisionInstance;

/** A selector for decisions. */
@FunctionalInterface
public interface DecisionSelector {
  /**
   * Checks if the decision instance matches the selector.
   *
   * @param instance the decision instance
   * @return {@code true} if the decision matches, otherwise {@code false}
   */
  boolean test(DecisionInstance instance);

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
   * Applies the given filter to limit the search of decision instances that match the selector.
   * Default: no filtering.
   *
   * @param filter the filter used to limit the decision instance search
   */
  default void applyFilter(final DecisionInstanceFilter filter) {}

  /**
   * Combines two decision selectors together.
   *
   * @param other decision selector to be added.
   * @return the combined decision selector
   */
  default DecisionSelector and(DecisionSelector other) {
    final DecisionSelector self = this;

    return new DecisionSelector() {
      @Override
      public boolean test(final DecisionInstance decisionInstance) {
        return self.test(decisionInstance) && other.test(decisionInstance);
      }

      @Override
      public String describe() {
        return String.format("%s, %s", self.describe(), other.describe());
      }

      @Override
      public void applyFilter(final DecisionInstanceFilter filter) {
        self.applyFilter(filter);
        other.applyFilter(filter);
      }
    };
  }
}
