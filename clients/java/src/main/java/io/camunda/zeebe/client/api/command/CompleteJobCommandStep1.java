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

import io.camunda.zeebe.client.api.response.CompleteJobResponse;
import java.io.InputStream;
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
  }
}
