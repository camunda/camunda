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
package io.zeebe.client.api.clients;

import io.zeebe.client.api.commands.ActivateJobsCommandStep1;
import io.zeebe.client.api.commands.CompleteJobCommandStep1;
import io.zeebe.client.api.commands.FailJobCommandStep1;
import io.zeebe.client.api.commands.UpdateRetriesJobCommandStep1;
import io.zeebe.client.api.subscription.JobWorkerBuilderStep1;

/**
 * A client with access to all job-related operation:
 * <li>complete a job
 * <li>mark a job as failed
 * <li>update the retries of a job
 */
public interface JobClient {

  /**
   * Command to complete a job.
   *
   * <pre>
   * long jobKey = ..;
   *
   * jobClient
   *  .newCompleteCommand(jobKey)
   *  .payload(json)
   *  .send();
   * </pre>
   *
   * <p>If the job is linked to a workflow instance then this command will complete the related
   * activity and continue the flow.
   *
   * @param jobKey the key which identifies the job
   * @return a builder for the command
   */
  CompleteJobCommandStep1 newCompleteCommand(long jobKey);

  /**
   * Command to mark a job as failed.
   *
   * <pre>
   * long jobKey = ..;
   *
   * jobClient
   *  .newFailCommand(jobKey)
   *  .retries(3)
   *  .send();
   * </pre>
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * subscription. Otherwise, an incident is created for this job.
   *
   * @param jobKey the key which identifies the job
   * @return a builder for the command
   */
  FailJobCommandStep1 newFailCommand(long jobKey);

  /**
   * Command to update the retries of a job.
   *
   * <pre>
   * long jobKey = ..;
   *
   * jobClient
   *  .newUpdateRetriesCommand(jobKey)
   *  .retries(3)
   *  .send();
   * </pre>
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * subscription and a related incident will be marked as resolved.
   *
   * @param jobKey the key of the job to update
   * @return a builder for the command
   */
  UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(long jobKey);

  /**
   * Registers a new job worker for jobs of a given type.
   *
   * <p>After registration, the broker activates available jobs and assigns them to this worker. It
   * then publishes them to the client. The given worker is called for every received job, works on
   * them and eventually completes them.
   *
   * <pre>
   * JobWorker worker = jobClient
   *  .newWorker()
   *  .jobType("payment")
   *  .handler(paymentHandler)
   *  .open();
   *
   * ...
   * worker.close();
   * </pre>
   *
   * Example JobHandler implementation:
   *
   * <pre>
   * public class PaymentHandler implements JobHandler
   * {
   *   &#64;Override
   *   public void handle(JobClient client, JobEvent jobEvent)
   *   {
   *     String json = jobEvent.getPayload();
   *     // modify payload
   *
   *     client
   *      .newCompleteCommand()
   *      .event(jobEvent)
   *      .payload(json)
   *      .send();
   *   }
   * };
   * </pre>
   *
   * @return a builder for the worker registration
   */
  JobWorkerBuilderStep1 newWorker();

  /**
   * Command to activate multiple jobs of a given type.
   *
   * <pre>
   * jobClient
   *  .newActivateJobsCommand()
   *  .jobType("payment")
   *  .amount(10)
   *  .workerName("paymentWorker")
   *  .timeout(Duration.ofMinutes(10))
   *  .send();
   * </pre>
   *
   * <p>The command will try to activate maximal {@code amount} jobs of given {@code jobType}. If
   * less then {@code amount} jobs of the {@code jobType} are available for activation the returned
   * list will have fewer elements.
   *
   * @return a builder for the command
   */
  ActivateJobsCommandStep1 newActivateJobsCommand();
}
