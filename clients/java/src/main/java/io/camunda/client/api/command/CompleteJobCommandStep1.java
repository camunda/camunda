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
import java.util.function.Function;

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
   * Initializes the job result to allow followup actions to be configured.
   *
   * @return the builder for this command.
   */
  CompleteJobCommandStep1 withResult(
      Function<CompleteJobCommandJobResultStep, CompleteJobResult> consumer);

  interface CompleteJobCommandJobResultStep {
    /**
     * Initializes the job result to allow corrections or a denial to be configured.
     *
     * <p>This method is used to apply changes to user task attributes (such as {@code assignee},
     * {@code priority}, {@code dueDate}, and so on) or explicitly deny a user task lifecycle
     * transition.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * client.newCompleteJobCommand(jobKey)
     *     .withResult(r -> r.forUserTask()
     *      .correctAssignee("john_doe")                 // dynamically reassigns the task to 'john_doe'
     *      .correctPriority(84)                         // adjusts the priority of the task
     *      .correctDueDate("2024-11-22T11:44:55.0000Z")) // sets a new due date
     *      .send();
     * }</pre>
     *
     * @return the builder for this command.
     */
    CompleteUserTaskJobResultStep1 forUserTask();

    /**
     * Initialized the job result to allow activation of elements in an ad-hoc sub process.
     *
     * <p>This method is used to activate elements as a followup of a job completion. It will
     * activate elements by id and variables provided will be created in the scope of the created
     * element.
     *
     * <pre>{@code
     * client.newCompleteJobCommand(jobKey)
     *  .withResult(r -> r.forAdHocSubProcess()
     *    .activateElement("elementId")           // Activate the element with id 'elementId'
     *    .variable("key", "value")               // Create variable in the scope of 'elementId'
     *    .activateElement("anotherElementId"))   // Activate another element with id 'anotherElementId'
     *    .variable("key", "value")               // Create a variable in the scope of 'anotherElementId'
     *    .send();
     *
     * }</pre>
     *
     * @return the builder for this command.
     */
    CompleteAdHocSubProcessResultStep1 forAdHocSubProcess();
  }
}
