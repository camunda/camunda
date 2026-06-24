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

import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.response.Variable;

/** A selector for process variables. */
@FunctionalInterface
public interface VariableSelector {

  /**
   * Checks if the variable matches the selector.
   *
   * @param variable the process variable
   * @return {@code true} if the variable matches, otherwise {@code false}
   */
  boolean test(Variable variable);

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
   * Applies the given filter to limit the search of variables that match the selector. Default: no
   * filtering.
   *
   * @param filter the filter used to limit the variable search
   */
  default void applyFilter(final VariableFilter filter) {}

  /**
   * Combines two variable selectors together.
   *
   * @param other variable selector to be added.
   * @return the combined variable selector
   */
  default VariableSelector and(final VariableSelector other) {
    final VariableSelector self = this;

    return new VariableSelector() {
      @Override
      public boolean test(final Variable variable) {
        return self.test(variable) && other.test(variable);
      }

      @Override
      public String describe() {
        return String.format("%s, %s", self.describe(), other.describe());
      }

      @Override
      public void applyFilter(final VariableFilter filter) {
        self.applyFilter(filter);
        other.applyFilter(filter);
      }
    };
  }
}
