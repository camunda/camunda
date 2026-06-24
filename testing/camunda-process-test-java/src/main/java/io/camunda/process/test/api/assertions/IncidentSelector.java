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

import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.response.Incident;

/** A selector for incidents. */
@FunctionalInterface
public interface IncidentSelector {

  /**
   * Checks if the incident matches the selector.
   *
   * @param incident the incident
   * @return {@code true} if the incident matches, otherwise {@code false}
   */
  boolean test(Incident incident);

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
   * Applies the given filter to limit the search of incidents that match the selector. Default: no
   * filtering.
   *
   * @param filter the filter used to limit the incident search
   */
  default void applyFilter(final IncidentFilter filter) {}

  /**
   * Combines two incident selectors together.
   *
   * @param other incident selector to be added.
   * @return the combined incident selector
   */
  default IncidentSelector and(IncidentSelector other) {
    final IncidentSelector self = this;

    return new IncidentSelector() {
      @Override
      public boolean test(final Incident incident) {
        return self.test(incident) && other.test(incident);
      }

      @Override
      public String describe() {
        return String.format("%s, %s", self.describe(), other.describe());
      }

      @Override
      public void applyFilter(final IncidentFilter filter) {
        self.applyFilter(filter);
        other.applyFilter(filter);
      }
    };
  }
}
