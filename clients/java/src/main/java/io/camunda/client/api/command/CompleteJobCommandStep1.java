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

import io.camunda.client.api.response.CompleteJobResponse;
import java.io.InputStream;
import java.util.Map;
import java.util.function.UnaryOperator;

public interface CompleteJobCommandStep1
    extends CommandWithCommunicationApiStep<CompleteJobCommandStep1>,
        FinalCommandStep<CompleteJobResponse>,
        CommandWithVariables<CompleteJobCommandStep1> {

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables (JSON) as String
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  @Override
  CompleteJobCommandStep1 variables(String variables);

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables as object
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  @Override
  CompleteJobCommandStep1 variables(Object variables);

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables (JSON) as stream
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  @Override
  CompleteJobCommandStep1 variables(InputStream variables);

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables as map
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  @Override
  CompleteJobCommandStep1 variables(Map<String, Object> variables);

  /**
   * Set a single variable to complete the job with.
   *
   * @param key the key of the variable as string
   * @param value the value of the variable as object
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  @Override
  CompleteJobCommandStep1 variable(String key, Object value);

  /**
   * Sets the result of the completed job, allowing the worker to apply corrections to user task
   * attributes or explicitly deny the user task lifecycle transition.
   *
   * <p>The {@link CompleteUserTaskJobResult} object provides a flexible way to:
   *
   * <ul>
   *   <li>Correct user task attributes such as {@code assignee}, {@code dueDate}, {@code priority},
   *       and more.
   *   <li>Deny the lifecycle transition associated with the user task.
   * </ul>
   *
   * <pre>{@code
   * final CompleteJobResult jobResult =
   *     new CompleteJobResult()
   *         .correctAssignee("newAssignee") // dynamically assigns the task
   *         .correctPriority(42);           // updates the task priority
   *
   * client.newCompleteJobCommand(jobKey)
   *     .withResult(jobResult)
   *     .send();
   * }</pre>
   *
   * @param jobResult the result of the job, containing corrections and/or a denial flag.
   * @return the builder for this command. Call {@link #send()} to finalize the command and send it
   *     to the broker.
   * @apiNote This API is currently relevant only for user task listeners.
   */
  CompleteJobCommandStep1 withResult(CompleteUserTaskJobResult jobResult);

  /**
   * Modifies the result of the completed job using a lambda expression, allowing the worker to
   * dynamically apply corrections to user task attributes or explicitly deny the user task
   * lifecycle transition.
   *
   * <p>This is a convenience method for {@link #withResult(CompleteUserTaskJobResult)}, allowing
   * modifications to be applied directly via a functional interface rather than constructing the
   * {@link CompleteUserTaskJobResult} manually, enabling:
   *
   * <ul>
   *   <li>Correcting user task attributes such as {@code assignee}, {@code dueDate}, {@code
   *       priority}, and more.
   *   <li>Denying the lifecycle transition associated with the user task.
   * </ul>
   *
   * <p>The lambda expression receives the current {@link CompleteUserTaskJobResult}, which can be
   * modified as needed. If no result has been set yet, a default {@link CompleteUserTaskJobResult}
   * is provided for modification.
   *
   * <pre>{@code
   * client.newCompleteJobCommand(jobKey)
   *     .withResult(r -> r.deny(true))
   *     .send();
   * }</pre>
   *
   * @param jobResultModifier a function to modify the {@link CompleteUserTaskJobResult}.
   * @return the builder for this command. Call {@link #send()} to finalize the command and send it
   *     to the broker.
   * @apiNote This API is currently relevant only for user task listeners.
   */
  CompleteJobCommandStep1 withResult(UnaryOperator<CompleteUserTaskJobResult> jobResultModifier);
}
