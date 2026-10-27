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

import io.camunda.client.api.response.GlobalTaskListenerResponse;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import java.util.List;

/**
 * Represents a request to create a global task listener.
 *
 * <p>Usage example:
 *
 * <pre>
 *   GlobalTaskListenerResponse response = camundaClient
 *       .newCreateGlobalTaskListenerRequest()
 *       .id("my-listener")
 *       .type("my-job-type")
 *       .eventTypes(GlobalTaskListenerEventType.CREATING, GlobalTaskListenerEventType.COMPLETING)
 *       .send()
 *       .join();
 * </pre>
 */
public interface CreateGlobalTaskListenerCommandStep1 {

  /**
   * Sets the ID of the global listener.
   *
   * @param id the ID of the global listener. Must not be null or empty.
   * @return this builder for method chaining
   */
  CreateGlobalTaskListenerCommandStep2 id(String id);

  interface CreateGlobalTaskListenerCommandStep2 {
    /**
     * Sets the job type of the global listener.
     *
     * @param type the name of the job type, used as a reference to specify which job workers
     *     request the respective listener job. Must not be null or empty.
     * @return this builder for method chaining
     */
    CreateGlobalTaskListenerCommandStep3 type(String type);
  }

  interface CreateGlobalTaskListenerCommandStep3 {

    /**
     * Sets the user task event types supported by the global listener.
     *
     * @param eventTypes list of user task event types that trigger the listener. Must not be null
     *     or empty.
     * @return this builder for method chaining
     */
    CreateGlobalTaskListenerCommandStep4 eventTypes(List<GlobalTaskListenerEventType> eventTypes);

    /**
     * Sets the user task event types supported by the global listener.
     *
     * @param eventTypes list of user task event types that trigger the listener. Must not be null
     *     or empty.
     * @return this builder for method chaining
     */
    CreateGlobalTaskListenerCommandStep4 eventTypes(GlobalTaskListenerEventType... eventTypes);

    /**
     * Adds support for a user task event type to the global listener.
     *
     * @param eventType user task event types that should trigger the listener. Must not be null.
     * @return this builder for method chaining
     */
    CreateGlobalTaskListenerCommandStep4 eventType(GlobalTaskListenerEventType eventType);
  }

  interface CreateGlobalTaskListenerCommandStep4
      extends CreateGlobalTaskListenerCommandStep1,
          CreateGlobalTaskListenerCommandStep2,
          CreateGlobalTaskListenerCommandStep3,
          FinalCommandStep<GlobalTaskListenerResponse> {

    // Optional parameters
    /**
     * Sets the number of retries for the global listener.
     *
     * @param retries maximum number of retries attempted by the listener job in case of failure.
     *     Must be an integer greater or equal to 1.
     * @return this builder for method chaining
     */
    CreateGlobalTaskListenerCommandStep4 retries(Integer retries);

    /**
     * Sets whether the global listener should be executed before or after the model-level ones.
     *
     * @param afterNonGlobal if true, the global listener is executed after the model-level ones. If
     *     false, it is executed before. Defaults to false if not set.
     * @return this builder for method chaining
     */
    CreateGlobalTaskListenerCommandStep4 afterNonGlobal(Boolean afterNonGlobal);

    /**
     * Sets that the global listener should be executed before the model-level ones.
     *
     * @return this builder for method chaining
     */
    CreateGlobalTaskListenerCommandStep4 beforeNonGlobal();

    /**
     * Sets that the global listener should be executed after the model-level ones.
     *
     * @return this builder for method chaining
     */
    CreateGlobalTaskListenerCommandStep4 afterNonGlobal();

    /**
     * Sets the priority of the global listener.
     *
     * <p>Global task listeners with higher priority are executed first.
     *
     * @param priority the priority of the global listener. Must be an integer between 0 and 100
     *     included.
     * @return this builder for method chaining
     */
    CreateGlobalTaskListenerCommandStep4 priority(Integer priority);
  }
}
