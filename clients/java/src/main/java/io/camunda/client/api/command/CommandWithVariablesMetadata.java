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

import io.camunda.client.api.ExperimentalApi;
import java.io.InputStream;
import java.util.Map;

/**
 * Adds variants of the variables methods of {@link CommandWithVariables} that also declare how
 * individual variables are treated, for example which protections apply to their values.
 *
 * <p>Only the commands that support variables metadata implement this interface.
 *
 * @param <T> the type of the command, returned to continue building it
 */
@ExperimentalApi("https://github.com/camunda/camunda/pull/68611")
public interface CommandWithVariablesMetadata<T> extends CommandWithVariables<T> {

  /**
   * Like {@link CommandWithVariables#variables(String)}, and declares how individual variables are
   * treated.
   *
   * @param variables the variables JSON document as String
   * @param variablesMetadata the metadata of the variables, keyed by variable name. Variables
   *     without an entry get none. Like the variables, it replaces all metadata set on the command
   *     before.
   * @return the builder for this command
   * @throws IllegalArgumentException if the metadata names a variable that is not set by this
   *     command
   */
  T variables(String variables, Map<String, VariableMetadataInput> variablesMetadata);

  /**
   * Like {@link CommandWithVariables#variables(Object)}, with variables metadata. See {@link
   * #variables(String, Map)}.
   */
  T variables(Object variables, Map<String, VariableMetadataInput> variablesMetadata);

  /**
   * Like {@link CommandWithVariables#variables(InputStream)}, with variables metadata. See {@link
   * #variables(String, Map)}.
   */
  T variables(InputStream variables, Map<String, VariableMetadataInput> variablesMetadata);

  /**
   * Like {@link CommandWithVariables#variables(Map)}, with variables metadata. See {@link
   * #variables(String, Map)}.
   */
  T variables(Map<String, Object> variables, Map<String, VariableMetadataInput> variablesMetadata);

  /**
   * Like {@link CommandWithVariables#variable(String, Object)}, and declares how the variable is
   * treated.
   *
   * @param key the key of the variable as string
   * @param value the value of the variable as object
   * @param variableMetadata the metadata of the variable. Like the variable, it replaces all
   *     metadata set on the command before.
   * @return the builder for this command
   */
  T variable(String key, Object value, VariableMetadataInput variableMetadata);

  /**
   * Like {@link CommandWithVariables#addVariable(String, Object)}, with variables metadata. The
   * metadata replaces the metadata set for the same variable before. The metadata of the other
   * variables is kept.
   */
  T addVariable(String key, Object value, VariableMetadataInput variableMetadata);

  /**
   * Like {@link CommandWithVariables#addVariables(Map)}, with variables metadata. The metadata
   * replaces the metadata set for the same variables before. The metadata of the other variables is
   * kept.
   *
   * @throws IllegalArgumentException if the metadata names a variable that is not set by this
   *     command
   */
  T addVariables(
      Map<String, Object> variables, Map<String, VariableMetadataInput> variablesMetadata);
}
