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
package io.camunda.zeebe.client.api.worker;

import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;

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
   *  .variables(json)
   *  .send();
   * </pre>
   *
   * <p>If the job is linked to a process instance then this command will complete the related
   * activity and continue the flow.
   *
   * @param jobKey the key which identifies the job
   * @return a builder for the command
   */
  CompleteJobCommandStep1 newCompleteCommand(long jobKey);

  /**
   * Command to complete a job.
   *
   * <pre>
   * ActivatedJob job = ..;
   *
   * jobClient
   *  .newCompleteCommand(job)
   *  .variables(json)
   *  .send();
   * </pre>
   *
   * <p>If the job is linked to a process instance then this command will complete the related
   * activity and continue the flow.
   *
   * @param job the activated job
   * @return a builder for the command
   */
  CompleteJobCommandStep1 newCompleteCommand(ActivatedJob job);

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
   * Command to mark a job as failed.
   *
   * <pre>
   * ActivatedJob job = ..;
   *
   * jobClient
   *  .newFailCommand(job)
   *  .retries(3)
   *  .send();
   * </pre>
   *
   * <p>If the given retries are greater than zero then this job will be picked up again by a job
   * subscription. Otherwise, an incident is created for this job.
   *
   * @param job the activated job
   * @return a builder for the command
   */
  FailJobCommandStep1 newFailCommand(ActivatedJob job);

  /**
   * Command to report a business error (i.e. non-technical) that occurs while processing a job.
   *
   * <pre>
   * long jobKey = ...;
   * String code = ...;
   *
   * jobClient
   *  .newThrowErrorCommand(jobKey)
   *  .errorCode(code)
   *  .send();
   * </pre>
   *
   * <p>The error is handled in the process by an error catch event. If there is no error catch
   * event with the specified errorCode then an incident will be raised instead.
   *
   * @param jobKey the key which identifies the job
   * @return a builder for the command
   */
  ThrowErrorCommandStep1 newThrowErrorCommand(long jobKey);

  /**
   * Command to report a business error (i.e. non-technical) that occurs while processing a job.
   *
   * <pre>
   * ActivatedJob job = ...;
   * String code = ...;
   *
   * jobClient
   *  .newThrowErrorCommand(job)
   *  .errorCode(code)
   *  .send();
   * </pre>
   *
   * <p>The error is handled in the process by an error catch event. If there is no error catch
   * event with the specified errorCode then an incident will be raised instead.
   *
   * @param job the activated job
   * @return a builder for the command
   */
  ThrowErrorCommandStep1 newThrowErrorCommand(ActivatedJob job);

  /**
   * Command to activate multiple jobs of a given type.
   *
   * <pre>
   * jobClient
   *  .newActivateJobsCommand()
   *  .jobType("payment")
   *  .maxJobsToActivate(10)
   *  .workerName("paymentWorker")
   *  .timeout(Duration.ofMinutes(10))
   *  .send();
   * </pre>
   *
   * <p>The command will try to use {@code maxJobsToActivate} for given {@code jobType}. If less
   * then the requested {@code maxJobsToActivate} jobs of the {@code jobType} are available for
   * activation the returned list will have fewer elements.
   *
   * @return a builder for the command
   */
  ActivateJobsCommandStep1 newActivateJobsCommand();
}
