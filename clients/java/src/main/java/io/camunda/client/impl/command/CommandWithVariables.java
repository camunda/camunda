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
package io.camunda.client.impl.command;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.VariableMetadataInput;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class CommandWithVariables<T> {

  protected final JsonMapper objectMapper;
  private final Map<String, Object> accumulatedVariables = new HashMap<>();
  private final Map<String, VariableMetadataInput> accumulatedVariablesMetadata =
      new LinkedHashMap<>();

  public CommandWithVariables(final JsonMapper jsonMapper) {
    objectMapper = jsonMapper;
  }

  public T variables(final InputStream variables) {
    return variables(variables, Collections.emptyMap());
  }

  public T variables(
      final InputStream variables, final Map<String, VariableMetadataInput> variablesMetadata) {
    ArgumentUtil.ensureNotNull("variables", variables);
    ArgumentUtil.ensureNotNull("variablesMetadata", variablesMetadata);
    final String validatedVariables = objectMapper.validateJson("variables", variables);
    replaceAccumulatedVariables(validatedVariables);
    replaceAccumulatedVariablesMetadata(variablesMetadata);
    return setVariables(validatedVariables);
  }

  public T variables(final String variables) {
    return variables(variables, Collections.emptyMap());
  }

  public T variables(
      final String variables, final Map<String, VariableMetadataInput> variablesMetadata) {
    ArgumentUtil.ensureNotNull("variables", variables);
    ArgumentUtil.ensureNotNull("variablesMetadata", variablesMetadata);
    final String validatedVariables = objectMapper.validateJson("variables", variables);
    replaceAccumulatedVariables(validatedVariables);
    replaceAccumulatedVariablesMetadata(variablesMetadata);
    return setVariables(validatedVariables);
  }

  public T variables(final Map<String, Object> variables) {
    return variables(variables, Collections.emptyMap());
  }

  public T variables(
      final Map<String, Object> variables,
      final Map<String, VariableMetadataInput> variablesMetadata) {
    ArgumentUtil.ensureNotNull("variables", variables);
    ArgumentUtil.ensureNotNull("variablesMetadata", variablesMetadata);
    final String serializedVariables = objectMapper.toJson(variables);
    replaceAccumulatedVariables(variables);
    replaceAccumulatedVariablesMetadata(variablesMetadata);
    return setVariables(serializedVariables);
  }

  public T variables(final Object variables) {
    return variables(variables, Collections.emptyMap());
  }

  public T variables(
      final Object variables, final Map<String, VariableMetadataInput> variablesMetadata) {
    ArgumentUtil.ensureNotNull("variables", variables);
    ArgumentUtil.ensureNotNull("variablesMetadata", variablesMetadata);
    final String serializedVariables = objectMapper.toJson(variables);
    final Object transformedVariables = objectMapper.transform(variables, Object.class);
    replaceAccumulatedVariables(ArgumentUtil.ensureJsonObject("variables", transformedVariables));
    replaceAccumulatedVariablesMetadata(variablesMetadata);
    return setVariables(serializedVariables);
  }

  public T variable(final String key, final Object value) {
    return variable(key, value, null);
  }

  public T variable(
      final String key, final Object value, final VariableMetadataInput variableMetadata) {
    ArgumentUtil.ensureNotNull("key", key);
    accumulatedVariables.clear();
    accumulatedVariables.put(key, value);
    replaceAccumulatedVariablesMetadata(
        variableMetadata == null
            ? Collections.emptyMap()
            : Collections.singletonMap(key, variableMetadata));
    return setVariables(objectMapper.toJson(accumulatedVariables));
  }

  public T addVariable(final String key, final Object value) {
    return addVariable(key, value, null);
  }

  public T addVariable(
      final String key, final Object value, final VariableMetadataInput variableMetadata) {
    ArgumentUtil.ensureNotNull("key", key);
    accumulatedVariables.put(key, value);
    if (variableMetadata != null) {
      accumulatedVariablesMetadata.put(key, variableMetadata);
    }
    return setVariables(objectMapper.toJson(accumulatedVariables));
  }

  public T addVariables(final Map<String, Object> variables) {
    return addVariables(variables, Collections.emptyMap());
  }

  public T addVariables(
      final Map<String, Object> variables,
      final Map<String, VariableMetadataInput> variablesMetadata) {
    ArgumentUtil.ensureNotNull("variables", variables);
    ArgumentUtil.ensureNotNull("variablesMetadata", variablesMetadata);
    accumulatedVariables.putAll(variables);
    accumulatedVariablesMetadata.putAll(validatedVariablesMetadata(variablesMetadata));
    return setVariables(objectMapper.toJson(accumulatedVariables));
  }

  private void replaceAccumulatedVariables(final String variables) {
    if (variables == null || variables.trim().isEmpty()) {
      replaceAccumulatedVariables(Collections.emptyMap());
      return;
    }

    final Object parsedVariables = objectMapper.fromJson(variables, Object.class);
    if (parsedVariables == null) {
      replaceAccumulatedVariables(Collections.emptyMap());
      return;
    }
    replaceAccumulatedVariables(ArgumentUtil.ensureJsonObject("variables", parsedVariables));
  }

  protected final void resetAccumulatedVariables() {
    accumulatedVariables.clear();
    accumulatedVariablesMetadata.clear();
  }

  protected final void replaceAccumulatedVariables(final Map<String, Object> variables) {
    accumulatedVariables.clear();
    accumulatedVariables.putAll(variables);
  }

  private void replaceAccumulatedVariablesMetadata(
      final Map<String, VariableMetadataInput> variablesMetadata) {
    final Map<String, VariableMetadataInput> replacement =
        validatedVariablesMetadata(variablesMetadata);
    accumulatedVariablesMetadata.clear();
    accumulatedVariablesMetadata.putAll(replacement);
  }

  private static Map<String, VariableMetadataInput> validatedVariablesMetadata(
      final Map<String, VariableMetadataInput> variablesMetadata) {
    final Map<String, VariableMetadataInput> result = new LinkedHashMap<>();
    variablesMetadata.forEach(
        (name, value) -> {
          ArgumentUtil.ensureNotNull("variable name in variablesMetadata", name);
          ArgumentUtil.ensureNotNull("variablesMetadata of variable '" + name + "'", value);
          result.put(name, value);
        });
    return result;
  }

  private T setVariables(final String variables) {
    return setVariablesInternal(
        variables, Collections.unmodifiableMap(accumulatedVariablesMetadata));
  }

  protected abstract T setVariablesInternal(String variables);

  /**
   * Called on every change of the variables, with the complete variables metadata of the command,
   * keyed by variable name. The map is empty when none was given, in which case the request should
   * not carry any variables metadata.
   *
   * <p>Commands whose request supports variables metadata override this. By default non-empty
   * variables metadata is rejected rather than silently dropped, as a dropped protection would leak
   * the value.
   */
  protected T setVariablesInternal(
      final String variables, final Map<String, VariableMetadataInput> variablesMetadata) {
    if (!variablesMetadata.isEmpty()) {
      throw new UnsupportedOperationException(
          getClass().getSimpleName() + " does not support variables metadata");
    }
    return setVariablesInternal(variables);
  }
}
