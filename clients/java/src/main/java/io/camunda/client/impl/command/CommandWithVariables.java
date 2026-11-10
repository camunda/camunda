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
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class CommandWithVariables<T> {

  protected final JsonMapper objectMapper;
  private final Map<String, Object> accumulatedVariables = new HashMap<>();

  public CommandWithVariables(final JsonMapper jsonMapper) {
    objectMapper = jsonMapper;
  }

  public T variables(final InputStream variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return apply(replacingWithJson(objectMapper.validateJson("variables", variables)));
  }

  public T variables(final String variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return apply(replacingWithJson(objectMapper.validateJson("variables", variables)));
  }

  public T variables(final Map<String, Object> variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return apply(replacingWithMap(variables));
  }

  public T variables(final Object variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return apply(replacingWithObject(variables));
  }

  public T variable(final String key, final Object value) {
    ArgumentUtil.ensureNotNull("key", key);
    return apply(replacingWithEntry(key, value));
  }

  public T addVariable(final String key, final Object value) {
    ArgumentUtil.ensureNotNull("key", key);
    return apply(adding(Collections.singletonMap(key, value)));
  }

  public T addVariables(final Map<String, Object> variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return apply(adding(variables));
  }

  /**
   * Hands the new variables to the command and only then makes them the accumulated variables, so
   * that a call that fails leaves what was accumulated before untouched.
   */
  protected T apply(final VariablesUpdate update) {
    final T result = setVariablesInternal(update.serializedVariables);
    replaceAccumulatedVariables(update.variables);
    return result;
  }

  /** The variables that replace all accumulated variables, from a validated JSON document. */
  protected final VariablesUpdate replacingWithJson(final String validatedVariables) {
    if (validatedVariables == null || validatedVariables.trim().isEmpty()) {
      return new VariablesUpdate(validatedVariables, new HashMap<>(), true);
    }
    final Object parsedVariables = objectMapper.fromJson(validatedVariables, Object.class);
    if (parsedVariables == null) {
      return new VariablesUpdate(validatedVariables, new HashMap<>(), true);
    }
    return new VariablesUpdate(
        validatedVariables,
        new HashMap<>(ArgumentUtil.ensureJsonObject("variables", parsedVariables)),
        true);
  }

  /** The variables that replace all accumulated variables, from a map. */
  protected final VariablesUpdate replacingWithMap(final Map<String, Object> variables) {
    return new VariablesUpdate(objectMapper.toJson(variables), new HashMap<>(variables), true);
  }

  /** The variables that replace all accumulated variables, from an object. */
  protected final VariablesUpdate replacingWithObject(final Object variables) {
    final String serializedVariables = objectMapper.toJson(variables);
    final Object transformedVariables = objectMapper.transform(variables, Object.class);
    return new VariablesUpdate(
        serializedVariables,
        new HashMap<>(ArgumentUtil.ensureJsonObject("variables", transformedVariables)),
        true);
  }

  /** A single variable that replaces all accumulated variables. */
  protected final VariablesUpdate replacingWithEntry(final String key, final Object value) {
    final Map<String, Object> variables = new HashMap<>();
    variables.put(key, value);
    return new VariablesUpdate(objectMapper.toJson(variables), variables, true);
  }

  /** The accumulated variables with the given variables added to them. */
  protected final VariablesUpdate adding(final Map<String, Object> variables) {
    final Map<String, Object> allVariables = new HashMap<>(accumulatedVariables);
    allVariables.putAll(variables);
    return new VariablesUpdate(objectMapper.toJson(allVariables), allVariables, false);
  }

  protected final void resetAccumulatedVariables() {
    accumulatedVariables.clear();
  }

  protected final void replaceAccumulatedVariables(final Map<String, Object> variables) {
    accumulatedVariables.clear();
    accumulatedVariables.putAll(variables);
  }

  protected abstract T setVariablesInternal(String variables);

  /**
   * The complete variables of the command after a call, computed before anything is changed.
   *
   * <p>Everything that can fail, such as validating and serializing, happens while this is created.
   */
  protected static final class VariablesUpdate {
    final String serializedVariables;
    final Map<String, Object> variables;
    final boolean replacesAll;

    private VariablesUpdate(
        final String serializedVariables,
        final Map<String, Object> variables,
        final boolean replacesAll) {
      this.serializedVariables = serializedVariables;
      this.variables = variables;
      this.replacesAll = replacesAll;
    }
  }
}
