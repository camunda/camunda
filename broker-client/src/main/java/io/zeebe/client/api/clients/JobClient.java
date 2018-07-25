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

import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.subscription.JobWorkerBuilderStep1;

/**
 * A client with access to all job-related operation:
 * <li>create a (standalone) job
 * <li>complete a job
 * <li>mark a job as failed
 * <li>update the retries of a job
 */
public interface JobClient {

  /**
   * Command to create a new (standalone) job. The job is not linked to a workflow instance.
   *
   * <pre>
   * jobClient
   *  .newCreateCommand()
   *  .type("my-todos")
   *  .payload(json)
   *  .send();
   * </pre>
   *
   * @return a builder for the command
   */
  CreateJobCommandStep1 newCreateCommand();

  /**
   * Command to complete a job.
   *
   * <pre>
   * jobClient
   *  .newCompleteCommand(jobEvent)
   *  .payload(json)
   *  .send();
   * </pre>
   *
   * The job is specified by the given event. The event must be the latest event of the job to
   * ensure that the command is based on the latest state of the job. If it's not the latest one
   * then the command is rejected.
   *
   * <p>If the job is linked to a workflow instance then this command will complete the related
   * activity and continue the flow.
   *
   * @param event the latest job event
   * @return a builder for the command
   */
  CompleteJobCommandStep1 newCompleteCommand(JobEvent event);

  /**
   * Command to mark a job as failed.
   *
   * <pre>
   * jobClient
   *  .newFailCommand(jobEvent)
   *  .retries(jobEvent.getRetries() - 1)
   *  .send();
   * </pre>
   *
   * The job is specified by the given event. The event must be the latest event of the job to
   * ensure that the command is based on the latest state of the job. If it's not the latest one
   * then the command is rejected.
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * subscription. Otherwise, an incident is created for this job.
   *
   * @param event the latest job event
   * @return a builder for the command
   */
  FailJobCommandStep1 newFailCommand(JobEvent event);

  /**
   * Command to update the retries of a job.
   *
   * <pre>
   * jobClient
   *  .newUpdateRetriesCommand(jobEvent)
   *  .retries(3)
   *  .send();
   * </pre>
   *
   * The job is specified by the given event. The event must be the latest event of the job to
   * ensure that the command is based on the latest state of the job. If it's not the latest one
   * then the command is rejected.
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * subscription and a related incident will be marked as resolved.
   *
   * @param event the latest job event
   * @return a builder for the command
   */
  UpdateRetriesJobCommandStep1 newUpdateRetriesCommand(JobEvent event);

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
}
