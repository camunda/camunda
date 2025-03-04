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
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public interface CompleteJobCommandStep1
    extends CommandWithCommunicationApiStep<CompleteJobCommandStep1>,
        FinalCommandStep<CompleteJobResponse> {

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables (JSON) as stream
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteJobCommandStep1 variables(InputStream variables);

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables (JSON) as String
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteJobCommandStep1 variables(String variables);

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables as map
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteJobCommandStep1 variables(Map<String, Object> variables);

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables as object
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteJobCommandStep1 variables(Object variables);

  /**
   * Set a single variable to complete the job with.
   *
   * @param key the key of the variable as string
   * @param value the value of the variable as object
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteJobCommandStep1 variable(String key, Object value);

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
   *     .withResult()
   *     .correctAssignee("john_doe")                 // dynamically reassigns the task to 'john_doe'
   *     .correctPriority(84)                         // adjusts the priority of the task
   *     .correctDueDate("2024-11-22T11:44:55.0000Z") // sets a new due date
   *     .send();
   * }</pre>
   *
   * @return the builder for this command.
   * @apiNote Currently, this API is relevant only for user task listeners.
   */
  CompleteJobCommandStep2 withResult();

  /**
   * Sets the result of the completed job, allowing the worker to apply corrections to user task
   * attributes or explicitly deny the user task lifecycle transition.
   *
   * <p>The {@link CompleteJobResult} object provides a flexible way to:
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
  CompleteJobCommandStep1 withResult(CompleteJobResult jobResult);

  /**
   * Modifies the result of the completed job using a lambda expression, allowing the worker to
   * dynamically apply corrections to user task attributes or explicitly deny the user task
   * lifecycle transition.
   *
   * <p>This is a convenience method for {@link #withResult(CompleteJobResult)}, allowing
   * modifications to be applied directly via a functional interface rather than constructing the
   * {@link CompleteJobResult} manually, enabling:
   *
   * <ul>
   *   <li>Correcting user task attributes such as {@code assignee}, {@code dueDate}, {@code
   *       priority}, and more.
   *   <li>Denying the lifecycle transition associated with the user task.
   * </ul>
   *
   * <p>The lambda expression receives the current {@link CompleteJobResult}, which can be modified
   * as needed. If no result has been set yet, a default {@link CompleteJobResult} is provided for
   * modification.
   *
   * <pre>{@code
   * client.newCompleteJobCommand(jobKey)
   *     .withResult(r -> r.deny(true))
   *     .send();
   * }</pre>
   *
   * @param jobResultModifier a function to modify the {@link CompleteJobResult}.
   * @return the builder for this command. Call {@link #send()} to finalize the command and send it
   *     to the broker.
   * @apiNote This API is currently relevant only for user task listeners.
   */
  CompleteJobCommandStep1 withResult(UnaryOperator<CompleteJobResult> jobResultModifier);

  interface CompleteJobCommandStep2 extends FinalCommandStep<CompleteJobResponse> {

    /**
     * Indicates whether the worker denies the work, i.e. explicitly doesn't approve it. For
     * example, a user task listener can deny the completion of a task by setting this flag to true.
     * In this example, the completion of a task is represented by a job that the worker can
     * complete as denied. As a result, the completion request is rejected and the task remains
     * active. Defaults to {@code false}.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * client.newCompleteJobCommand(jobKey)
     *     .withResult()
     *     .deny(true)
     *     .send();
     * }</pre>
     *
     * @param isDenied indicates if the worker has denied the reason for the job
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep2 deny(boolean isDenied);

    /**
     * Indicates whether the worker denies the work, i.e. explicitly doesn't approve it and provides
     * the reason to deny. As a result, the completion request is rejected and the task remains
     * active.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * client.newCompleteJobCommand(jobKey)
     *     .withResult()
     *     .deny(true, "Reason to deny lifecycle transition")
     *     .send();
     * }</pre>
     *
     * @param isDenied indicates if the worker has denied the reason for the job
     * @param deniedReason indicates the reason why the worker denied the job
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep2 deny(boolean isDenied, String deniedReason);

    /**
     * Indicates the reason why the worker denied the work. For example, a user task listener can
     * deny the completion of a task by setting the deny flag to true and specifying the reason to
     * deny. In this example, the completion of a task is represented by a job that the worker can
     * complete as denied and provided the reason to deny. As a result, the completion request is
     * rejected and the task remains active. Defaults to an empty string.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * client.newCompleteJobCommand(jobKey)
     *     .withResult()
     *     .deny(true)
     *     .deniedReason("Reason to deny lifecycle transition")
     *     .send();
     * }</pre>
     *
     * @param deniedReason indicates the reason why the worker denied the job
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep2 deniedReason(String deniedReason);

    /**
     * Applies corrections to the user task attributes.
     *
     * <p>This method allows the worker to modify key attributes of the user task (such as {@code
     * assignee}, {@code candidateGroups}, and so on)
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * final JobResultCorrections corrections = new JobResultCorrections()
     *     .assignee("john_doe")               // reassigns the task to 'john_doe'
     *     .priority(80)                       // sets a high priority
     *     .dueDate("2024-01-01T12:00:00Z")    // updates the due date
     *     .candidateGroups(List.of("sales"))  // allows the 'sales' group to claim the task
     *     .candidateUsers(List.of("alice"));  // allows 'alice' to claim the task
     *
     * client.newCompleteJobCommand(jobKey)
     *     .withResult()
     *     .correct(corrections)
     *     .send();
     * }</pre>
     *
     * @param corrections the corrections to apply to the user task.
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep2 correct(JobResultCorrections corrections);

    /**
     * Dynamically applies corrections to the user task attributes using a lambda expression.
     *
     * <p>This method is a functional alternative to {@link #correct(JobResultCorrections)}. It
     * allows the worker to modify key user task attributes (such as {@code assignee}, {@code
     * dueDate}, {@code priority}, and so on) directly via a lambda expression. The lambda receives
     * the current {@link JobResultCorrections} instance, which can be updated as needed. If no
     * corrections have been set yet, a default {@link JobResultCorrections} instance is provided.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * client.newCompleteJobCommand(jobKey)
     *     .withResult()
     *     .correct(corrections -> corrections
     *         .assignee("john_doe")               // dynamically reassigns the task to 'john_doe'
     *         .priority(80)                       // adjusts the priority of the task
     *         .dueDate("2024-01-01T12:00:00Z")    // updates the due date
     *         .candidateGroups(List.of("sales"))  // allows the 'sales' group to claim the task
     *         .candidateUsers(List.of("alice")))  // allows 'alice' to claim the task
     *     .send();
     * }</pre>
     *
     * @param corrections a lambda expression to modify the {@link JobResultCorrections}.
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep2 correct(UnaryOperator<JobResultCorrections> corrections);

    /**
     * Correct the assignee of the task.
     *
     * @param assignee assignee of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep2 correctAssignee(String assignee);

    /**
     * Correct the due date of the task.
     *
     * @param dueDate due date of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep2 correctDueDate(String dueDate);

    /**
     * Correct the follow up date of the task.
     *
     * @param followUpDate follow up date of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep2 correctFollowUpDate(String followUpDate);

    /**
     * Correct the candidate users of the task.
     *
     * @param candidateUsers candidate users of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep2 correctCandidateUsers(List<String> candidateUsers);

    /**
     * Correct the candidate groups of the task.
     *
     * @param candidateGroups candidate groups of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep2 correctCandidateGroups(List<String> candidateGroups);

    /**
     * Correct the priority of the task.
     *
     * @param priority priority of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep2 correctPriority(Integer priority);

    /**
     * Marks the completion of configuring the result of the job.
     *
     * <p>This method is optional and can be used to indicate that the result configuration (such as
     * corrections or denial) is complete. It allows calling methods unrelated to the job result.
     *
     * <p>Calling this method has no effect on the final command sent to the broker. It is provided
     * for readability and organizational clarity in method chaining.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * client.newCompleteJobCommand(jobKey)
     *     .withResult()
     *     .correctAssignee("john_doe")
     *     .resultDone() // explicitly marks the end of result configuration
     *     .variable("we_can", "still_set_vars")
     *     .send();
     * }</pre>
     *
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep1 resultDone();
  }
}
