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
   * The result of the completed job as determined by the worker.
   *
   * @return the builder for this command.
   */
  CompleteJobCommandStep2 result();

  interface CompleteJobCommandStep2 extends FinalCommandStep<CompleteJobResponse> {

    /**
     * Indicates whether the worker denies the work, i.e. explicitly doesn't approve it. For
     * example, a Task Listener can deny the completion of a task by setting this flag to true. In
     * this example, the completion of a task is represented by a job that the worker can complete
     * as denied. As a result, the completion request is rejected and the task remains active.
     * Defaults to false.
     *
     * @param denied indicates if the worker has denied the reason for the job
     * @return the builder for this command.
     */
    CompleteJobCommandStep2 denied(boolean denied);

    /**
     * Attributes that were corrected by the worker. The below attributes can be corrected and
     * additional attributes will be ignored: `assignee` - reset by providing an empty String
     * `dueDate` - reset by providing an empty String `followUpDate` - reset by providing an empty
     * String `candidateGroups` - reset by providing an empty list `candidateUsers` - reset by
     * providing an empty list `priority` - minimum 0, maximum 100, default 50 Omitting any of the
     * attributes will preserve the persisted attribute's value.
     *
     * @return the builder for this command.
     */
    CompleteJobCommandStep3 corrections();
  }

  interface CompleteJobCommandStep3 extends CompleteJobCommandStep2 {

    /**
     * Correct the assignee of the task.
     *
     * @param assignee assignee of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep3 assignee(String assignee);

    /**
     * Correct the due date of the task.
     *
     * @param dueDate due date of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep3 dueDate(String dueDate);

    /**
     * Correct the follow up date of the task.
     *
     * @param followUpDate follow up date of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep3 followUpDate(String followUpDate);

    /**
     * Correct the candidate users of the task.
     *
     * @param candidateUsers candidate users of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep3 candidateUsers(List<String> candidateUsers);

    /**
     * Correct the candidate groups of the task.
     *
     * @param candidateGroups candidate groups of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep3 candidateGroups(List<String> candidateGroups);

    /**
     * Correct the priority of the task.
     *
     * @param priority priority of the task
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CompleteJobCommandStep3 priority(int priority);
  }
}
