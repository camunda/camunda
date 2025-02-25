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

import io.camunda.zeebe.client.api.ExperimentalApi;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1.StreamJobsCommandStep3;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.time.Duration;

/**
 * A client with access to all job-related operation:
 * <li>complete a job
 * <li>mark a job as failed
 * <li>update the retries of a job
 *
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.worker.JobClient}
 */
@Deprecated
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

  /**
   * Activates and streams jobs of a specific type.
   *
   * <pre>{@code
   * final Consumer<ActivatedJob> consumer = ...; // do something with the consumed job
   * final ZeebeFuture<StreamJobsResponse> stream = jobClient
   *  .newStreamJobsCommand()
   *  .jobType("payment")
   *  .consumer(consumer)
   *  .workerName("paymentWorker")
   *  .timeout(Duration.ofMinutes(10))
   *  .send();
   *
   *  stream.whenComplete((ok, error) -> {
   *    // recreate stream if necessary
   *    // be careful if you've cancelled the stream explicitly to not recreate it if shutting down
   *  });
   *
   *  // You can later terminate the stream by cancelling the future
   *  stream.cancel(true);
   * }</pre>
   *
   * <h2>Stream or Activate?</h2>
   *
   * <p>As opposed to {@link #newActivateJobsCommand()}, which polls each partition until it has
   * activated enough jobs or a timeout has elapsed, this command opens a long living stream onto
   * which activated jobs are pushed. This typically results in lower latency, as jobs are activated
   * and pushed out immediately, instead of waiting to be polled.
   *
   * <h2>Limitations</h2>
   *
   * <p>This feature is still under development; as such, there is currently no way to rate limit
   * how many jobs are streamed over a single call. This can be mitigated by opening more streams of
   * the same type, which will ensure work is fairly load balanced.
   *
   * <p>Additionally, only jobs which are created, retried, or timed out <em>after</em> the command
   * has been registered will be streamed out. For older jobs, you must still use the {@link
   * #newActivateJobsCommand()}. It's generally recommended that you use the {@link JobWorker} API
   * to avoid having to coordinate both calls.
   *
   * <h2>Activation</h2>
   *
   * <p>Jobs activated via this command will use the given worker name, activation time out, and
   * fetch variables parameters in the same way as the {@link #newActivateJobsCommand()}.
   *
   * <h2>Termination</h2>
   *
   * <p>The stream can be explicitly cancelled by performing one of the following:
   *
   * <ul>
   *   <li>Closing the Zeebe client
   *   <li>Cancelling the result of {@link StreamJobsCommandStep3#send()} via {@link
   *       ZeebeFuture#cancel(boolean)} (the argument is irrelevant)
   *   <li>Setting a {@link StreamJobsCommandStep3#requestTimeout(Duration)}; the stream will be
   *       closed once this time out is reached. By default, there is no request time out at all.
   *       <strong>It's recommended to assign a long-ish time out and recreate your streams from
   *       time to time to ensure good load balancing across gateways.</strong>
   * </ul>
   *
   * NOTE: streams can be closed for various reasons - for example, the server is restarting. As
   * such, it's recommended to add listeners to the resulting future to handle such cases and reopen
   * streams if necessary.
   *
   * @return a builder for the command
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/11231")
  StreamJobsCommandStep1 newStreamJobsCommand();
}
