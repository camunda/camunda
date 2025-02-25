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

import io.camunda.zeebe.client.api.response.CompleteUserTaskResponse;
import java.util.Map;

/**
 * The user task completion currently only accepts variables as a {@link Map} due to the current
 * request handling before sending it the gateway. The list of options might be extended in the
 * future.
 *
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.CompleteUserTaskCommandStep1}
 */
@Deprecated
public interface CompleteUserTaskCommandStep1 extends FinalCommandStep<CompleteUserTaskResponse> {

  /**
   * Set the custom action to complete the user task with.
   *
   * @param action the action value
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteUserTaskCommandStep1 action(String action);

  /**
   * Set the variables to complete the user task with.
   *
   * @param variables the variables as map
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteUserTaskCommandStep1 variables(Map<String, Object> variables);
}
