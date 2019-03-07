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
package io.zeebe.client.api.commands;

import java.io.InputStream;
import java.util.Map;

public interface SetVariablesCommandStep1 {
  /**
   * Sets the variables document from a JSON stream.
   *
   * @param variables the variables JSON document as stream
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  SetVariablesCommandStep2 variables(InputStream variables);

  /**
   * Sets the variables document from a JSON string.
   *
   * @param variables the variables JSON document as String
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  SetVariablesCommandStep2 variables(String variables);

  /**
   * Sets the variables document from a map.
   *
   * @param variables the variables document as map
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  SetVariablesCommandStep2 variables(Map<String, Object> variables);

  /**
   * Sets the variables document from an object, which will be serialized into a JSON document.
   *
   * @param variables the variables document as object
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  SetVariablesCommandStep2 variables(Object variables);

  interface SetVariablesCommandStep2 extends FinalCommandStep<Void> {
    // the place for new optional parameters

    /**
     * If true, the variables will be merged strictly into the local scope (as indicated by
     * elementInstanceKey); this means the variables is not propagated to upper scopes.
     *
     * <p>For example, let's say we have two scopes, '1' and '2', with each having effective
     * variables as:
     *
     * <ol>
     *   <li>1 => `{ "foo" : 2 }`
     *   <li>2 => `{ "bar" : 1 }`
     * </ol>
     *
     * <p>If we send an update request with elementInstanceKey = 2, a new document of `{ "foo" : 5
     * }`, and local is true, then scope 1 will be unchanged, and scope 2 will now be `{ "bar" : 1,
     * "foo" 5 }`.
     *
     * <p>If local was false, however, then scope 1 would be `{ "foo": 5 }`, and scope 2 would be `{
     * "bar" : 1 }`.
     *
     * @param local whether or not to update only the local scope
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    SetVariablesCommandStep2 local(boolean local);
  }
}
