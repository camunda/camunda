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

import io.camunda.client.api.response.UnassignClientFromGroupResponse;

public interface UnassignClientFromGroupCommandStep1 {

  /**
   * Sets the client ID.
   *
   * @param clientId the id of the client
   * @return the builder for this command
   */
  UnassignClientFromGroupCommandStep2 clientId(String clientId);

  interface UnassignClientFromGroupCommandStep2
      extends FinalCommandStep<UnassignClientFromGroupResponse> {

    /**
     * Sets the group ID.
     *
     * @param groupId the id of the group
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    UnassignClientFromGroupCommandStep2 groupId(String groupId);
  }
}
