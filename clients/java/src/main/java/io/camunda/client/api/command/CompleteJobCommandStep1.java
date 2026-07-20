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
        JobCallbackFinalCommandStep<CompleteJobResponse>,
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

  /**
   * Sets the lease token identifying the job's activation, fencing this command against a
   * superseded activation of the same job. Obtain it from {@link
   * io.camunda.client.api.response.ActivatedJob#getLeaseToken() ActivatedJob#getLeaseToken()}.
   *
   * <p>For a leased job, the matching token must be supplied to prove the command comes from the
   * worker that holds the current lease; a command with no token is rejected. A command carrying a
   * stale token is likewise rejected, fencing the job against a superseded activation (e.g. after
   * the job timed out or failed and was re-activated by another worker). A job that was activated
   * without a lease requires no token.
   *
   * <p>When this command is created from an activated job (e.g. {@code
   * newCompleteCommand(activatedJob)}) the job's lease token is carried automatically, so this
   * method is only needed when building the command from a job key.
   *
   * @param leaseToken the opaque lease token the worker received when the job was activated
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteJobCommandStep1 withLeaseToken(String leaseToken);

  /**
   * Assigns the given business id to the job's root process instance as part of completing the job,
   * letting a worker derive the identifier from work it just performed.
   *
   * <p>The assignment is single and irreversible and is only accepted while business id uniqueness
   * is disabled. Only artifacts created after the assignment carry the business id;
   * already-existing ones are not enriched. Completing with a business id that differs from one
   * already assigned rejects the whole completion, leaving the job open; re-sending the identical
   * business id is an idempotent no-op.
   *
   * <p>Passing {@code null} leaves the business id unset (no assignment is requested). A blank
   * business id is rejected with an {@link IllegalArgumentException}.
   *
   * @param businessId the business id to assign to the root process instance
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   * @throws IllegalArgumentException if {@code businessId} is blank
   */
  CompleteJobCommandStep1 withBusinessId(String businessId);

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
