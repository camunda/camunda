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

import io.camunda.zeebe.client.api.response.AssignUserTaskResponse;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.AssignUserTaskCommandStep1}
 */
@Deprecated
public interface AssignUserTaskCommandStep1 extends FinalCommandStep<AssignUserTaskResponse> {

  /**
   * Set the custom action to assign the user task with.
   *
   * @param action the action value
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  AssignUserTaskCommandStep1 action(String action);

  /**
   * Set the assignee to set for the user task.
   *
   * @param assignee the assignee to set
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  AssignUserTaskCommandStep1 assignee(String assignee);

  /**
   * Flag to allow overriding an existing assignee for the user task without unassigning it first.
   *
   * @param allowOverride allow overriding an existing assignee
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  AssignUserTaskCommandStep1 allowOverride(boolean allowOverride);
}
