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
package io.camunda.process.test.impl.assertions;

import io.camunda.client.api.search.response.Variable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared helper for fetching process instance variables from the data source. */
class VariableFetcher {

  private static final Logger LOG = LoggerFactory.getLogger(VariableFetcher.class);

  private final CamundaDataSource dataSource;

  VariableFetcher(final CamundaDataSource dataSource) {
    this.dataSource = dataSource;
  }

  Map<String, String> getGlobalProcessInstanceVariables(final long processInstanceKey) {
    final List<Variable> variables =
        dataSource.findGlobalVariablesByProcessInstanceKey(processInstanceKey);

    return toMap(ensureVariablesAreNotTruncated(variables));
  }

  Map<String, String> getLocalProcessInstanceVariables(
      final long processInstanceKey, final long elementInstanceKey) {

    final List<Variable> variables =
        dataSource.findVariables(
            filter -> filter.processInstanceKey(processInstanceKey).scopeKey(elementInstanceKey));

    return toMap(ensureVariablesAreNotTruncated(variables));
  }

  private List<Variable> ensureVariablesAreNotTruncated(final List<Variable> variablesToCheck) {
    return variablesToCheck.stream()
        .map(
            variable -> {
              if (variable.isTruncated()) {
                return fetchCompleteVariableByKey(variable);
              } else {
                return variable;
              }
            })
        .collect(Collectors.toList());
  }

  private Variable fetchCompleteVariableByKey(final Variable variable) {
    try {

      return dataSource.getVariable(variable.getVariableKey());
    } catch (final Throwable t) {

      final String expandVariableException =
          String.format(
              "Unable to fetch complete variable data for truncated variable [name: %s]. Will attempt to "
                  + "complete the assertion based on the truncated value which may lead to errors.",
              variable.getName());
      LOG.warn(expandVariableException, t);

      return variable;
    }
  }

  private Map<String, String> toMap(final List<Variable> variables) {
    return variables.stream()
        // We're deliberately switching from the Collectors.toMap collector to a custom
        // implementation because it's allowed to have Camunda Variables with null values
        // However, the toMap collector does not allow null values and would throw an exception.
        // See this Stack Overflow issue for more context: https://stackoverflow.com/a/24634007
        .collect(HashMap::new, (m, v) -> m.put(v.getName(), v.getValue()), HashMap::putAll);
  }
}
