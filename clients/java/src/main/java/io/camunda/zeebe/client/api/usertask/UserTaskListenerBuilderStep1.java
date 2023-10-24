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
package io.camunda.zeebe.client.api.usertask;

import io.camunda.zeebe.client.api.command.CommandWithOneOrMoreTenantsStep;
import io.camunda.zeebe.client.api.worker.JobWorker;

public interface UserTaskListenerBuilderStep1 {
  /**
   * Set the type of jobs to work on.
   *
   * @param type the type of jobs (e.g. "payment")
   * @return the builder for this worker
   */
  UserTaskListenerBuilderStep2 eventType(String type);

  interface UserTaskListenerBuilderStep2 {
    UserTaskListenerBuilderStep3 listenerName(String listenerName);
  }

  interface UserTaskListenerBuilderStep3 {
    /**
     * Set the handler to process the jobs. At the end of the processing, the handler should
     * complete the job or mark it as failed;
     *
     * <p>Example JobHandler implementation:
     *
     * <pre>
     * public final class PaymentHandler implements JobHandler
     * {
     *   &#64;Override
     *   public void handle(JobClient client, JobEvent jobEvent)
     *   {
     *     String json = jobEvent.getVariables();
     *     // modify variables
     *
     *     client
     *      .newCompleteCommand(jobEvent.getKey())
     *      .variables(json)
     *      .send();
     *   }
     * };
     * </pre>
     *
     * The handler must be thread-safe.
     *
     * @param handler the handle to process the jobs
     * @return the builder for this worker
     */
    UserTaskListenerBuilderStep4 handler(UserTaskListener handler);
  }

  interface UserTaskListenerBuilderStep4
      extends CommandWithOneOrMoreTenantsStep<UserTaskListenerBuilderStep3> {

    /**
     * Open the worker and start to work on available tasks.
     *
     * @return the worker
     */
    JobWorker open();
  }
}
