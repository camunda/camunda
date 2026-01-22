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
package io.camunda.client.lifecycle;

import io.camunda.client.CamundaClient;

public interface CamundaClientLifecycleAware {

  /**
   * Called when a CamundaClient is started.
   *
   * @param client the client that was started
   */
  default void onStart(final CamundaClient client) {
    // Default implementation for backwards compatibility
  }

  /**
   * Called when a CamundaClient is started.
   *
   * @param client the client that was started
   * @param clientName the configured name of the client, or null in single-client mode
   */
  default void onStart(final CamundaClient client, final String clientName) {
    // Default implementation calls the legacy method for backwards compatibility
    onStart(client);
  }

  /**
   * Called when a CamundaClient is about to stop.
   *
   * @param client the client that is stopping
   */
  default void onStop(final CamundaClient client) {
    // Default implementation for backwards compatibility
  }

  /**
   * Called when a CamundaClient is about to stop.
   *
   * @param client the client that is stopping
   * @param clientName the configured name of the client, or null in single-client mode
   */
  default void onStop(final CamundaClient client, final String clientName) {
    // Default implementation calls the legacy method for backwards compatibility
    onStop(client);
  }
}
