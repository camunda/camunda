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
package io.camunda.client.impl.search.request;

import io.camunda.client.api.search.filter.VariableValueFilter;
import io.camunda.client.impl.search.filter.VariableValueFilterImpl;
import io.camunda.client.protocol.rest.*;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SearchRequestMapper {

  public static List<VariableValueFilterRequest> toVariableValueFilterRequest(
      final List<Consumer<VariableValueFilter>> variableValueFilters,
      final Function<VariableValueFilterImpl, VariableValueFilterRequest> valueExtractor) {
    return variableValueFilters.stream()
        .map(
            vvfc -> {
              final VariableValueFilterImpl vvf = new VariableValueFilterImpl();
              vvfc.accept(vvf);
              return vvf;
            })
        .map(valueExtractor)
        .collect(Collectors.toList());
  }

  public static List<VariableValueFilterRequest> toVariableValueFilterRequest(
      final Map<String, Object> variableValueFilters) {
    return variableValueFilters.entrySet().stream()
        .map(
            e -> {
              variableValueNullCheck(e.getValue());
              return new VariableValueFilterRequest()
                  .name(e.getKey())
                  .value(new StringFilterProperty().$eq(e.getValue().toString()));
            })
        .collect(Collectors.toList());
  }

  static void variableValueNullCheck(final Object value) {
    if (value == null) {
      throw new IllegalArgumentException("Variable value cannot be null");
    }
  }
}
