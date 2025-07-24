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

public interface CompleteAdHocSubProcessResultStep1 extends CompleteJobResult {

  /**
   * Adds an element to activate in the ad-hoc sub-process.
   *
   * @return this result
   */
  CompleteAdHocSubProcessResultStep2 activateElement(String elementId);

  /**
   * Indicates whether the completion condition of the ad-hoc sub-process is fulfilled.
   *
   * @return this result
   */
  CompleteAdHocSubProcessResultStep1 completionConditionFulfilled(
      boolean completionConditionFulfilled);

  /**
   * Indicates whether all remaining instances of the ad-hoc sub-process should be canceled.
   *
   * @return this result
   */
  CompleteAdHocSubProcessResultStep1 cancelRemainingInstances(boolean cancelRemainingInstances);

  interface CompleteAdHocSubProcessResultStep2
      extends CompleteAdHocSubProcessResultStep1,
          CommandWithVariables<CompleteAdHocSubProcessResultStep1> {
    /**
     * The variables that will be created on the activated element instance.
     *
     * @param variables the variables JSON document as String
     * @return the builder for this command.
     */
    @Override
    CompleteAdHocSubProcessResultStep1 variables(String variables);

    /**
     * The variables that will be created on the activated element instance.
     *
     * @param variables the variables document as object to be serialized to JSON
     * @return the builder for this command.
     */
    @Override
    CompleteAdHocSubProcessResultStep1 variables(Object variables);

    /**
     * The variables that will be created on the activated element instance.
     *
     * @param variables the variables JSON document as stream
     * @return the builder for this command.
     */
    @Override
    CompleteAdHocSubProcessResultStep1 variables(InputStream variables);

    /**
     * The variables that will be created on the activated element instance.
     *
     * @param variables the variables document as map
     * @return the builder for this command.
     */
    @Override
    CompleteAdHocSubProcessResultStep1 variables(Map<String, Object> variables);

    /**
     * A single variable that will be created on the activated element instance.
     *
     * @param key the key of the variable as string
     * @param value the value of the variable as object
     * @return the builder for this command.
     */
    @Override
    CompleteAdHocSubProcessResultStep1 variable(String key, Object value);
  }
}
