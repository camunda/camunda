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
package io.camunda.zeebe.client.api.command;

import java.io.InputStream;
import java.util.Map;

public interface CommandWithVariablesCommandStep<T> {

  /**
   * Set the variables to complete the command {@link T} with.
   *
   * @param variables the variables (JSON) as stream
   * @return the builder for this command.
   */
  T variables(InputStream variables);

  /**
   * Set the variables to complete the command {@link T} with.
   *
   * @param variables the variables (JSON) as String
   * @return the builder for this command.
   */
  T variables(String variables);

  /**
   * Set the variables to complete the command {@link T} with.
   *
   * @param variables the variables as map
   * @return the builder for this command.
   */
  T variables(Map<String, Object> variables);

  /**
   * Set the variables to complete the command {@link T} with.
   *
   * @param variables the variables as object
   * @return the builder for this command.
   */
  T variables(Object variables);

  /**
   * Set the single variable to complete the command {@link T} with.
   *
   * @param key the key of the variable as string
   * @param value the value of the variable as object
   * @return the builder for this command.
   */
  T variable(String key, Object value);
}
