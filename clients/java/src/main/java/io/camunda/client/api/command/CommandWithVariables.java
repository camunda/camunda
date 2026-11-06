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
package io.camunda.client.api.command;

import java.io.InputStream;
import java.util.Map;

public interface CommandWithVariables<T> {
  /**
   * Sets the variables from a JSON document, replacing any variables previously set on the command.
   * An empty document or JSON {@code null} is treated as empty variables. Any other document must
   * represent a JSON object.
   *
   * @param variables the variables JSON document as String
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   * @throws IllegalArgumentException if the JSON document represents a non-null value other than an
   *     object
   */
  T variables(String variables);

  /**
   * Like {@link #variables(String)}, and declares how individual variables are treated.
   *
   * @param variables the variables JSON document as String
   * @param variablesMetadata the metadata of the variables, keyed by variable name; variables
   *     without an entry get none. Must only name variables that are set by this command. Like the
   *     variables, it replaces the metadata set on the command before, so protections declared
   *     earlier are dropped unless they are declared again.
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   * @throws UnsupportedOperationException if the command does not support variables metadata
   */
  T variables(String variables, Map<String, VariableMetadataInput> variablesMetadata);

  /**
   * Sets the variables from an object, replacing any variables previously set on the command. The
   * object must serialize to a JSON object.
   *
   * @param variables the variables as object
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   * @throws IllegalArgumentException if the serialized value is not a JSON object
   */
  T variables(Object variables);

  /**
   * Like {@link #variables(Object)}, with variables metadata. See {@link #variables(String, Map)}.
   */
  T variables(Object variables, Map<String, VariableMetadataInput> variablesMetadata);

  /**
   * Sets the variables from a JSON document, replacing any variables previously set on the command.
   * JSON {@code null} is treated as empty variables. Any other document must represent a JSON
   * object.
   *
   * @param variables the variables JSON document as stream
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   * @throws IllegalArgumentException if the JSON document represents a non-null value other than an
   *     object
   */
  T variables(InputStream variables);

  /**
   * Like {@link #variables(InputStream)}, with variables metadata. See {@link #variables(String,
   * Map)}.
   */
  T variables(InputStream variables, Map<String, VariableMetadataInput> variablesMetadata);

  /**
   * Sets the variables from a map, replacing any variables previously set on the command.
   *
   * @param variables the variables as map
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  T variables(Map<String, Object> variables);

  /** Like {@link #variables(Map)}, with variables metadata. See {@link #variables(String, Map)}. */
  T variables(Map<String, Object> variables, Map<String, VariableMetadataInput> variablesMetadata);

  /**
   * Sets a single variable, replacing any variables previously set on the command.
   *
   * @param key the key of the variable as string
   * @param value the value of the variable as object
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  T variable(String key, Object value);

  /**
   * Like {@link #variable(String, Object)}, and declares how the variable is treated.
   *
   * @param key the key of the variable as string
   * @param value the value of the variable as object
   * @param variableMetadata the metadata of the variable, or {@code null} for none. Like the
   *     variable, it replaces the metadata set on the command before, so protections declared
   *     earlier are dropped unless they are declared again.
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   * @throws UnsupportedOperationException if the command does not support variables metadata
   */
  T variable(String key, Object value, VariableMetadataInput variableMetadata);

  /**
   * Adds a single variable to the command. Unlike {@link #variable(String, Object)}, this method
   * accumulates variables across multiple invocations, allowing you to build the variable map entry
   * by entry.
   *
   * @param key the key of the variable as string
   * @param value the value of the variable as object
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  T addVariable(String key, Object value);

  /**
   * Like {@link #addVariable(String, Object)}, with variables metadata. The metadata accumulates
   * too: protections declared earlier for the same variable are kept and the new ones are added.
   */
  T addVariable(String key, Object value, VariableMetadataInput variableMetadata);

  /**
   * Adds multiple variables to the command. Unlike {@link #variables(Map)}, this method accumulates
   * variables across multiple invocations, allowing you to build the variable map incrementally.
   *
   * @param variables the variables to add as map
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  T addVariables(Map<String, Object> variables);

  /**
   * Like {@link #addVariables(Map)}, with variables metadata. The metadata accumulates too:
   * protections declared earlier for the same variable are kept and the new ones are added.
   */
  T addVariables(
      Map<String, Object> variables, Map<String, VariableMetadataInput> variablesMetadata);
}
