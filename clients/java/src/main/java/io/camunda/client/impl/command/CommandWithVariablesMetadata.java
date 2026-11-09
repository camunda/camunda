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
import java.util.Map;

/**
 * Adds variables metadata to {@link CommandWithVariables}, for the commands that support it.
 *
 * <p>The variables and their metadata are always handed to the command and accumulated together, so
 * a call that fails, for example because of invalid metadata, leaves both untouched.
 */
public abstract class CommandWithVariablesMetadata<T> extends CommandWithVariables<T> {

  private final Map<String, VariableMetadataInput> accumulatedVariablesMetadata = new HashMap<>();

  public CommandWithVariablesMetadata(final JsonMapper jsonMapper) {
    super(jsonMapper);
  }

  public T variables(
      final InputStream variables, final Map<String, VariableMetadataInput> variablesMetadata) {
    ArgumentUtil.ensureNotNull("variables", variables);
    ArgumentUtil.ensureNotNull("variablesMetadata", variablesMetadata);
    return applyWithMetadata(
        replacingWithJson(objectMapper.validateJson("variables", variables)),
        validatedVariablesMetadata(variablesMetadata));
  }

  public T variables(
      final String variables, final Map<String, VariableMetadataInput> variablesMetadata) {
    ArgumentUtil.ensureNotNull("variables", variables);
    ArgumentUtil.ensureNotNull("variablesMetadata", variablesMetadata);
    return applyWithMetadata(
        replacingWithJson(objectMapper.validateJson("variables", variables)),
        validatedVariablesMetadata(variablesMetadata));
  }

  public T variables(
      final Map<String, Object> variables,
      final Map<String, VariableMetadataInput> variablesMetadata) {
    ArgumentUtil.ensureNotNull("variables", variables);
    ArgumentUtil.ensureNotNull("variablesMetadata", variablesMetadata);
    return applyWithMetadata(
        replacingWithMap(variables), validatedVariablesMetadata(variablesMetadata));
  }

  public T variables(
      final Object variables, final Map<String, VariableMetadataInput> variablesMetadata) {
    ArgumentUtil.ensureNotNull("variables", variables);
    ArgumentUtil.ensureNotNull("variablesMetadata", variablesMetadata);
    return applyWithMetadata(
        replacingWithObject(variables), validatedVariablesMetadata(variablesMetadata));
  }

  public T variable(
      final String key, final Object value, final VariableMetadataInput variableMetadata) {
    ArgumentUtil.ensureNotNull("key", key);
    ArgumentUtil.ensureNotNull("variableMetadata", variableMetadata);
    return applyWithMetadata(
        replacingWithEntry(key, value), Collections.singletonMap(key, variableMetadata));
  }

  public T addVariable(
      final String key, final Object value, final VariableMetadataInput variableMetadata) {
    ArgumentUtil.ensureNotNull("key", key);
    ArgumentUtil.ensureNotNull("variableMetadata", variableMetadata);
    return addVariables(
        Collections.singletonMap(key, value), Collections.singletonMap(key, variableMetadata));
  }

  public T addVariables(
      final Map<String, Object> variables,
      final Map<String, VariableMetadataInput> variablesMetadata) {
    ArgumentUtil.ensureNotNull("variables", variables);
    ArgumentUtil.ensureNotNull("variablesMetadata", variablesMetadata);
    final Map<String, VariableMetadataInput> addedMetadata =
        validatedVariablesMetadata(variablesMetadata);
    final VariablesUpdate update = adding(variables);

    final Map<String, VariableMetadataInput> allMetadata =
        new HashMap<>(accumulatedVariablesMetadata);
    allMetadata.putAll(addedMetadata);
    return applyWithMetadata(update, allMetadata);
  }

  /** Handles the calls without metadata: replacing the variables drops all metadata. */
  @Override
  protected final T apply(final VariablesUpdate update) {
    return applyWithMetadata(
        update, update.replacesAll ? Collections.emptyMap() : accumulatedVariablesMetadata);
  }

  /**
   * Puts the variables and their metadata into the request of the command. It is called whenever
   * the variables change, with all the variables and all the metadata of the command, because the
   * request has to carry both together. The metadata is keyed by variable name and empty if there
   * is none, in which case the request carries none.
   */
  protected abstract T setVariablesInternal(
      String variables, Map<String, VariableMetadataInput> variablesMetadata);

  @Override
  protected final T setVariablesInternal(final String variables) {
    return setVariablesInternal(variables, Collections.emptyMap());
  }

  /**
   * Hands the complete variables and variables metadata to the command and only then makes them the
   * accumulated state, so that a call that fails leaves what was accumulated before untouched.
   */
  private T applyWithMetadata(
      final VariablesUpdate update, final Map<String, VariableMetadataInput> variablesMetadata) {
    final Map<String, VariableMetadataInput> metadata = new HashMap<>(variablesMetadata);
    metadata
        .keySet()
        .forEach(
            name -> {
              if (!update.variables.containsKey(name)) {
                throw new IllegalArgumentException(
                    "Expected variablesMetadata to name only variables set by this command, but '"
                        + name
                        + "' is not one of them");
              }
            });
    final T result =
        setVariablesInternal(update.serializedVariables, Collections.unmodifiableMap(metadata));
    replaceAccumulatedVariables(update.variables);
    accumulatedVariablesMetadata.clear();
    accumulatedVariablesMetadata.putAll(metadata);
    return result;
  }

  private static Map<String, VariableMetadataInput> validatedVariablesMetadata(
      final Map<String, VariableMetadataInput> variablesMetadata) {
    final Map<String, VariableMetadataInput> result = new HashMap<>();
    variablesMetadata.forEach(
        (name, value) -> {
          ArgumentUtil.ensureNotNull("variable name in variablesMetadata", name);
          ArgumentUtil.ensureNotNull("variablesMetadata of variable '" + name + "'", value);
          result.put(name, value);
        });
    return result;
  }
}
