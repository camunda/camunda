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
    final String validatedVariables = objectMapper.validateJson("variables", variables);
    seedAccumulatedVariables(validatedVariables);
    return setVariablesInternal(validatedVariables);
  }

  public T variables(final String variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    final String validatedVariables = objectMapper.validateJson("variables", variables);
    seedAccumulatedVariables(validatedVariables);
    return setVariablesInternal(validatedVariables);
  }

  public T variables(final Map<String, Object> variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    final String serializedVariables = objectMapper.toJson(variables);
    seedAccumulatedVariables(serializedVariables);
    return setVariablesInternal(serializedVariables);
  }

  public T variables(final Object variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    final String serializedVariables = objectMapper.toJson(variables);
    seedAccumulatedVariables(serializedVariables);
    return setVariablesInternal(serializedVariables);
  }

  public T variable(final String key, final Object value) {
    ArgumentUtil.ensureNotNull("key", key);
    accumulatedVariables.clear();
    accumulatedVariables.put(key, value);
    return setVariablesInternal(objectMapper.toJson(accumulatedVariables));
  }

  public T addVariable(final String key, final Object value) {
    ArgumentUtil.ensureNotNull("key", key);
    accumulatedVariables.put(key, value);
    return setVariablesInternal(objectMapper.toJson(accumulatedVariables));
  }

  public T addVariables(final Map<String, Object> variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    accumulatedVariables.putAll(variables);
    return setVariablesInternal(objectMapper.toJson(accumulatedVariables));
  }

  private void seedAccumulatedVariables(final String variables) {
    accumulatedVariables.clear();
    final Object parsedVariables = objectMapper.fromJson(variables, Object.class);
    if (parsedVariables instanceof Map) {
      final Map<?, ?> variablesMap = (Map<?, ?>) parsedVariables;
      for (final Map.Entry<?, ?> entry : variablesMap.entrySet()) {
        final Object key = entry.getKey();
        if (key instanceof String) {
          accumulatedVariables.put((String) key, entry.getValue());
        }
      }
    }
  }

  protected abstract T setVariablesInternal(String variables);
}
