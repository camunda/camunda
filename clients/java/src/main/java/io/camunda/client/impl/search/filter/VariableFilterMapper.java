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
package io.camunda.client.impl.search.filter;

import io.camunda.client.api.search.filter.VariableValueFilter;
import io.camunda.client.api.search.request.SearchRequestBuilders;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VariableFilterMapper {

  public static List<VariableValueFilterProperty> toVariableValueFilterProperty(
      final List<Consumer<VariableValueFilter>> variableValueFilters) {
    return toVariableValueFilterProperty(
        variableValueFilters.stream()
            .map(SearchRequestBuilders::variableValueFilter)
            .map(
                TypedSearchRequestPropertyProvider
                    ::<VariableValueFilterProperty>provideSearchRequestProperty));
  }

  public static List<VariableValueFilterProperty> toVariableValueFilterProperty(
      final Map<String, Object> variableValueFilters) {
    return toVariableValueFilterProperty(
        variableValueFilters.entrySet().stream()
            .map(
                entry ->
                    new VariableValueFilterProperty()
                        .name(entry.getKey())
                        .value(
                            entry.getValue() == null
                                ? new AdvancedStringFilter()
                                : new AdvancedStringFilter().$eq(entry.getValue().toString()))));
  }

  static List<VariableValueFilterProperty> toVariableValueFilterProperty(
      final Stream<VariableValueFilterProperty> filterStream) {
    final List<String> violations = new ArrayList<>();
    final List<VariableValueFilterProperty> filters =
        filterStream
            .map(filter -> checkVariableValueNotNull(filter, violations))
            .collect(Collectors.toList());
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException(
          "Variable value filters contain violations: " + String.join(". ", violations));
    }
    return filters;
  }

  static VariableValueFilterProperty checkVariableValueNotNull(
      final VariableValueFilterProperty filter, final List<String> violations) {
    if (filter.getValue() == null || filter.getValue().equals(new AdvancedStringFilter())) {
      violations.add("Variable value cannot be null for variable '" + filter.getName() + "'");
    }
    return filter;
  }
}
