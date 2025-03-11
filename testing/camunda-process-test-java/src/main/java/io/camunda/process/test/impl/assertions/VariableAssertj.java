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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.search.response.Variable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.TerminalFailureException;

public class VariableAssertj extends AbstractAssert<VariableAssertj, String> {

  private final ObjectMapper jsonMapper = new ObjectMapper();

  private final CamundaDataSource dataSource;

  public VariableAssertj(final CamundaDataSource dataSource, final String failureMessagePrefix) {
    super(failureMessagePrefix, VariableAssertj.class);
    this.dataSource = dataSource;
  }

  public void hasVariableNames(final long processInstanceKey, final String... variableNames) {
    final AtomicReference<Map<String, String>> reference =
        new AtomicReference<>(Collections.emptyMap());

    try {
      Awaitility.await()
          .untilAsserted(
              () -> {
                final Map<String, String> variables =
                    getProcessInstanceVariables(processInstanceKey);
                reference.set(variables);

                assertThat(variables).containsKeys(variableNames);
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {

      final Map<String, String> actualVariables = reference.get();

      final List<String> missingVariableNames =
          Arrays.stream(variableNames)
              .filter(variableName -> !actualVariables.containsKey(variableName))
              .collect(Collectors.toList());

      final String failureMessage =
          String.format(
              "%s should have the variables %s but %s don't exist.",
              actual,
              AssertFormatUtil.formatNames(variableNames),
              AssertFormatUtil.formatNames(missingVariableNames));
      fail(failureMessage);
    }
  }

  public void hasVariable(
      final long processInstanceKey, final String variableName, final Object variableValue) {
    final JsonNode expectedValue = toJson(variableValue);

    final AtomicReference<Map<String, String>> reference =
        new AtomicReference<>(Collections.emptyMap());

    try {
      Awaitility.await()
          .untilAsserted(
              () -> {
                final Map<String, String> variables =
                    getProcessInstanceVariables(processInstanceKey);
                reference.set(variables);

                assertThat(variables).containsKey(variableName);

                final JsonNode actualValue = readJson(variables.get(variableName));
                assertThat(actualValue).isEqualTo(expectedValue);
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {

      final Map<String, String> actualVariables = reference.get();

      final String failureReason =
          Optional.ofNullable(actualVariables.get(variableName))
              .map(value -> String.format("was '%s'", value))
              .orElse("the variable doesn't exist");

      final String failureMessage =
          String.format(
              "%s should have a variable '%s' with value '%s' but %s.",
              actual, variableName, expectedValue, failureReason);
      fail(failureMessage);
    }
  }

  public void hasVariables(
      final long processInstanceKey, final Map<String, Object> expectedVariables) {
    final Map<String, JsonNode> expectedValues =
        expectedVariables.entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, entry -> toJson(entry.getValue())));

    final Set<String> expectedVariableNames = expectedVariables.keySet();

    final AtomicReference<Map<String, JsonNode>> reference =
        new AtomicReference<>(Collections.emptyMap());

    try {
      Awaitility.await()
          .untilAsserted(
              () -> {
                final Map<String, JsonNode> actualValues =
                    getProcessInstanceVariables(processInstanceKey).entrySet().stream()
                        .filter(entry -> expectedVariableNames.contains(entry.getKey()))
                        .collect(
                            Collectors.toMap(Entry::getKey, entry -> readJson(entry.getValue())));
                reference.set(actualValues);

                assertThat(actualValues).containsAllEntriesOf(expectedValues);
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {

      final Map<String, JsonNode> actualVariables = reference.get();

      final List<String> missingVariables =
          expectedVariableNames.stream()
              .filter(variableName -> !actualVariables.containsKey(variableName))
              .collect(Collectors.toList());

      String formattedMissingVariables = "";
      if (!missingVariables.isEmpty()) {
        formattedMissingVariables =
            String.format(
                " The variables %s don't exist.", AssertFormatUtil.formatNames(missingVariables));
      }

      final String failureMessage =
          String.format(
              "%s should have the variables %s but was %s.%s",
              actual,
              toJson(expectedVariables),
              toJson(actualVariables),
              formattedMissingVariables);
      fail(failureMessage);
    }
  }

  private Map<String, String> getProcessInstanceVariables(final long processInstanceKey) {
    return dataSource.findVariablesByProcessInstanceKey(processInstanceKey).stream()
        .collect(Collectors.toMap(Variable::getName, Variable::getValue));
  }

  private JsonNode readJson(final String value) {
    try {
      return jsonMapper.readValue(value, JsonNode.class);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(String.format("Failed to read JSON: '%s'", value), e);
    }
  }

  private JsonNode toJson(final Object value) {
    try {
      return jsonMapper.convertValue(value, JsonNode.class);
    } catch (final IllegalArgumentException e) {
      throw new RuntimeException(
          String.format("Failed to transform value to JSON: '%s'", value), e);
    }
  }
}
