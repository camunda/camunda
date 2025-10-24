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

import io.camunda.client.api.response.AssignClientToGroupResponse;

public interface AssignClientToGroupCommandStep1 {

  /**
   * Sets the ID of the client to be assigned to a group.
   *
   * @param clientId the ID of the client
   * @return the builder for this command.
   */
  AssignClientToGroupCommandStep2 clientId(String clientId);

  interface AssignClientToGroupCommandStep2 {

    /**
     * Sets the group ID.
     *
     * @param groupId the ID of the group
     * @return the builder for this command. Call {@link AssignClientToGroupCommandStep3#send()} to
     *     complete the command and send it to the broker.
     */
    AssignClientToGroupCommandStep3 groupId(String groupId);
  }

  interface AssignClientToGroupCommandStep3 extends FinalCommandStep<AssignClientToGroupResponse> {}
}
