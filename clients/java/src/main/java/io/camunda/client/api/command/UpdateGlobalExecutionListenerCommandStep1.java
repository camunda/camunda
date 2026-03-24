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

import io.camunda.client.api.response.GlobalExecutionListenerResponse;
import io.camunda.client.api.search.enums.GlobalExecutionListenerCategory;
import io.camunda.client.api.search.enums.GlobalExecutionListenerElementType;
import io.camunda.client.api.search.enums.GlobalExecutionListenerEventType;
import java.util.List;

public interface UpdateGlobalExecutionListenerCommandStep1 {

  /**
   * Sets the job type (required). Determines which job workers process the listener.
   *
   * @param type the job type
   * @return the next step
   */
  UpdateGlobalExecutionListenerCommandStep2 type(String type);

  interface UpdateGlobalExecutionListenerCommandStep2 {

    /**
     * Sets the event types for this listener (required).
     *
     * @param eventTypes the event types
     * @return the next step
     */
    UpdateGlobalExecutionListenerCommandStep3 eventTypes(
        List<GlobalExecutionListenerEventType> eventTypes);

    /**
     * Sets the event types for this listener (required).
     *
     * @param eventTypes the event types
     * @return the next step
     */
    UpdateGlobalExecutionListenerCommandStep3 eventTypes(
        GlobalExecutionListenerEventType... eventTypes);

    /**
     * Sets a single event type for this listener (required).
     *
     * @param eventType the event type
     * @return the next step
     */
    UpdateGlobalExecutionListenerCommandStep3 eventType(GlobalExecutionListenerEventType eventType);
  }

  interface UpdateGlobalExecutionListenerCommandStep3
      extends FinalCommandStep<GlobalExecutionListenerResponse> {

    /**
     * Sets the number of retries for the listener job.
     *
     * @param retries the number of retries
     * @return the builder for chaining
     */
    UpdateGlobalExecutionListenerCommandStep3 retries(int retries);

    /**
     * Sets whether the listener runs after BPMN-level listeners (default: false — global runs
     * first).
     *
     * @param afterNonGlobal true to run after BPMN-level listeners
     * @return the builder for chaining
     */
    UpdateGlobalExecutionListenerCommandStep3 afterNonGlobal(boolean afterNonGlobal);

    /**
     * Sets the execution priority among global listeners (higher = first).
     *
     * @param priority the execution priority
     * @return the builder for chaining
     */
    UpdateGlobalExecutionListenerCommandStep3 priority(int priority);

    /**
     * Sets the fine-grained BPMN element types this listener applies to.
     *
     * @param elementTypes the element types
     * @return the builder for chaining
     */
    UpdateGlobalExecutionListenerCommandStep3 elementTypes(
        List<GlobalExecutionListenerElementType> elementTypes);

    /**
     * Sets the fine-grained BPMN element types this listener applies to.
     *
     * @param elementTypes the element types
     * @return the builder for chaining
     */
    UpdateGlobalExecutionListenerCommandStep3 elementTypes(
        GlobalExecutionListenerElementType... elementTypes);

    /**
     * Sets the broad element type categories this listener applies to.
     *
     * @param categories the categories
     * @return the builder for chaining
     */
    UpdateGlobalExecutionListenerCommandStep3 categories(
        List<GlobalExecutionListenerCategory> categories);

    /**
     * Sets the broad element type categories this listener applies to.
     *
     * @param categories the categories
     * @return the builder for chaining
     */
    UpdateGlobalExecutionListenerCommandStep3 categories(
        GlobalExecutionListenerCategory... categories);
  }
}
