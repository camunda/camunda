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

import io.camunda.client.api.search.filter.HeaderValueFilter;
import io.camunda.client.api.search.request.SearchRequestBuilders;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.protocol.rest.HeaderValueFilterProperty;
import io.camunda.client.protocol.rest.StringFilterProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HeaderFilterMapper {

  public static List<HeaderValueFilterProperty> toHeaderValueFilterProperty(
      final List<Consumer<HeaderValueFilter>> headerValueFilters) {
    return toHeaderValueFilterProperty(
        headerValueFilters.stream()
            .map(SearchRequestBuilders::headerValueFilter)
            .map(
                TypedSearchRequestPropertyProvider
                    ::<HeaderValueFilterProperty>provideSearchRequestProperty));
  }

  public static List<HeaderValueFilterProperty> toHeaderValueFilterProperty(
      final Map<String, Object> headerValueFilters) {
    return toHeaderValueFilterProperty(
        headerValueFilters.entrySet().stream()
            .map(
                entry ->
                    new HeaderValueFilterProperty()
                        .name(entry.getKey())
                        .value(
                            entry.getValue() == null
                                ? new StringFilterProperty()
                                : new StringFilterProperty().$eq(entry.getValue().toString()))));
  }

  static List<HeaderValueFilterProperty> toHeaderValueFilterProperty(
      final Stream<HeaderValueFilterProperty> filterStream) {
    final List<String> violations = new ArrayList<>();
    final List<HeaderValueFilterProperty> filters =
        filterStream
            .map(filter -> checkHeaderValueNotNull(filter, violations))
            .collect(Collectors.toList());
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException(
          "Header value filters contain violations: " + String.join(". ", violations));
    }
    return filters;
  }

  static HeaderValueFilterProperty checkHeaderValueNotNull(
      final HeaderValueFilterProperty filter, final List<String> violations) {
    if (filter.getValue() == null || filter.getValue().equals(new StringFilterProperty())) {
      violations.add("Header value cannot be null for header '" + filter.getName() + "'");
    }
    return filter;
  }
}

