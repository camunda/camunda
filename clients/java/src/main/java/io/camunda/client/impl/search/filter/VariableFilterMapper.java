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

  public static List<VariableValueFilterRequest> toVariableValueFilterRequest(
      final List<Consumer<VariableValueFilter>> variableValueFilters) {
    return toVariableValueFilterRequest(
        variableValueFilters.stream()
            .map(SearchRequestBuilders::variableValueFilter)
            .map(
                TypedSearchRequestPropertyProvider
                    ::<VariableValueFilterRequest>provideSearchRequestProperty));
  }

  public static List<VariableValueFilterRequest> toVariableValueFilterRequest(
      final Map<String, Object> variableValueFilters) {
    return toVariableValueFilterRequest(
        variableValueFilters.entrySet().stream()
            .map(
                entry ->
                    new VariableValueFilterRequest()
                        .name(entry.getKey())
                        .value(
                            entry.getValue() == null
                                ? null
                                : new StringFilterProperty().$eq(entry.getValue().toString()))));
  }

  static List<VariableValueFilterRequest> toVariableValueFilterRequest(
      final Stream<VariableValueFilterRequest> filterStream) {
    final List<String> violations = new ArrayList<>();
    final List<VariableValueFilterRequest> filters =
        filterStream
            .map(filter -> checkVariableValueNotNull(filter, violations))
            .collect(Collectors.toList());
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException(
          "Variable value filters contain violations: " + String.join(". ", violations));
    }
    return filters;
  }

  static VariableValueFilterRequest checkVariableValueNotNull(
      final VariableValueFilterRequest filter, final List<String> violations) {
    if (filter.getValue() == null) {
      violations.add("Variable value cannot be null for variable '" + filter.getName() + "'");
    }
    return filter;
  }
}
