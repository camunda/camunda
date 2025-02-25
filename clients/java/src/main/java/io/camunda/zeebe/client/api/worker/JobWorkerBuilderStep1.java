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

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.ExperimentalApi;
import io.camunda.zeebe.client.api.command.CommandWithOneOrMoreTenantsStep;
import java.time.Duration;
import java.util.List;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.worker.JobWorkerBuilderStep1}
 */
@Deprecated
public interface JobWorkerBuilderStep1 {
  /**
   * Set the type of jobs to work on.
   *
   * @param type the type of jobs (e.g. "payment")
   * @return the builder for this worker
   */
  JobWorkerBuilderStep2 jobType(String type);

  interface JobWorkerBuilderStep2 {
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
    JobWorkerBuilderStep3 handler(JobHandler handler);
  }

  interface JobWorkerBuilderStep3 extends CommandWithOneOrMoreTenantsStep<JobWorkerBuilderStep3> {

    /**
     * Set the time for how long a job is exclusively assigned for this worker.
     *
     * <p>In this time, the job can not be assigned by other workers to ensure that only one worker
     * work on the job. When the time is over then the job can be assigned again by this or other
     * worker if it's not completed yet.
     *
     * <p>If no timeout is set, then the default is used from the configuration.
     *
     * @param timeout the time in milliseconds
     * @return the builder for this worker
     */
    JobWorkerBuilderStep3 timeout(long timeout);

    /**
     * Set the time for how long a job is exclusively assigned for this worker.
     *
     * <p>In this time, the job can not be assigned by other workers to ensure that only one worker
     * work on the job. When the time is over then the job can be assigned again by this or other
     * worker if it's not completed yet.
     *
     * <p>If no time is set then the default is used from the configuration.
     *
     * @param timeout the time as duration (e.g. "Duration.ofMinutes(5)")
     * @return the builder for this worker
     */
    JobWorkerBuilderStep3 timeout(Duration timeout);

    /**
     * Set the name of the worker owner.
     *
     * <p>This name is used to identify the worker to which a job is exclusively assigned to.
     *
     * <p>If no name is set then the default is used from the configuration.
     *
     * @param workerName the name of the worker (e.g. "payment-service")
     * @return the builder for this worker
     */
    JobWorkerBuilderStep3 name(String workerName);

    /**
     * Set the maximum number of jobs which will be exclusively activated for this worker at the
     * same time.
     *
     * <p>This is used to control the backpressure of the worker. When the maximum is reached then
     * the worker will stop activating new jobs in order to not overwhelm the client and give other
     * workers the chance to work on the jobs. The worker will try to activate new jobs again when
     * jobs are completed (or marked as failed).
     *
     * <p>If no maximum is set then the default, from the {@link ZeebeClientConfiguration}, is used.
     *
     * <p>Considerations:
     *
     * <ul>
     *   <li>A greater value can avoid situations in which the client waits idle for the broker to
     *       provide more jobs. This can improve the worker's throughput.
     *   <li>The memory used by the worker is linear with respect to this value.
     *   <li>The job's timeout starts to run down as soon as the broker pushes the job. Keep in mind
     *       that the following must hold to ensure fluent job handling: <code>
     *       time spent in queue + time job handler needs until job completion < job timeout</code>
     *
     * @param maxJobsActive the maximum jobs active by this worker
     * @return the builder for this worker
     */
    JobWorkerBuilderStep3 maxJobsActive(int maxJobsActive);

    /**
     * Set the maximal interval between polling for new jobs.
     *
     * <p>A job worker will automatically try to always activate new jobs after completing jobs. If
     * no jobs can be activated after completing the worker will periodically poll for new jobs.
     *
     * <p>If no poll interval is set then the default is used from the {@link
     * ZeebeClientConfiguration}
     *
     * @param pollInterval the maximal interval to check for new jobs
     * @return the builder for this worker
     */
    JobWorkerBuilderStep3 pollInterval(Duration pollInterval);

    /**
     * Set the request timeout for activate job request used to poll for new job.
     *
     * <p>If no request timeout is set then the default is used from the {@link
     * ZeebeClientConfiguration}
     *
     * <p>NOTE: the request time out defined here is only applied to the activate jobs command, i.e.
     * to polling for jobs, and is not applied to the job stream; use {@link
     * #streamTimeout(Duration)} for that.
     *
     * @param requestTimeout the request timeout for activate jobs request
     * @return the builder for this worker
     */
    JobWorkerBuilderStep3 requestTimeout(Duration requestTimeout);

    /**
     * Set a list of variable names which should be fetch on job activation.
     *
     * <p>The jobs which are activated by this worker will only contain variables from this list.
     *
     * <p>This can be used to limit the number of variables of the activated jobs.
     *
     * @param fetchVariables list of variables names to fetch on activation
     * @return the builder for this worker
     */
    JobWorkerBuilderStep3 fetchVariables(List<String> fetchVariables);

    /**
     * Set a list of variable names which should be fetch on job activation.
     *
     * <p>The jobs which are activated by this worker will only contain variables from this list.
     *
     * <p>This can be used to limit the number of variables of the activated jobs.
     *
     * @param fetchVariables list of variables names to fetch on activation
     * @return the builder for this worker
     */
    JobWorkerBuilderStep3 fetchVariables(String... fetchVariables);

    /**
     * Sets the backoff supplier. The supplier is called to determine the retry delay after each
     * failed request; the worker then waits until the returned delay has elapsed before sending the
     * next request. Note that this is used <strong>only</strong> for the polling mechanism -
     * failures in the {@link JobHandler} should be handled there, and retried there if need be.
     *
     * <p>By default, the supplier uses exponential back off, with an upper bound of 5 seconds. The
     * exponential backoff can be easily configured using {@link
     * BackoffSupplier#newBackoffBuilder()}.
     *
     * @param backoffSupplier supplies the retry delay after a failed request
     * @return the builder for this worker
     */
    JobWorkerBuilderStep3 backoffSupplier(BackoffSupplier backoffSupplier);

    /**
     * Opt-in feature flag to enable job streaming. If set as enabled, the job worker will use a mix
     * of streaming and polling to activate jobs. A long living stream will be opened onto which
     * jobs will be eagerly pushed, and the polling mechanism will be used strictly to fetch jobs
     * created <em>before</em> any streams were opened.
     *
     * <p>If the stream is closed, e.g. the server closed the connection, was restarted, etc., it
     * will be immediately recreated as long as the worker is opened.
     *
     * <p>NOTE: Job streaming is still under active development, and should be disabled if you
     * notice any issues.
     *
     * @return the builder for this worker
     */
    @ExperimentalApi("https://github.com/camunda/camunda/issues/11231")
    JobWorkerBuilderStep3 streamEnabled(boolean isStreamEnabled);

    /**
     * If streaming is enabled, sets a maximum lifetime for a given stream. Once this timeout is
     * reached, the stream is closed, such that no more jobs are activated and received. If the
     * worker is still open, then it will immediately open a new stream.
     *
     * <p>With no timeout, the stream will live as long as the client, or until the server closes
     * the connection.
     *
     * <p>It's recommended to set a relatively long timeout, to allow for streams to load balance
     * properly across your gateways.
     *
     * @param timeout a timeout, after which the stream is recreated
     * @return the builder for this worker
     */
    @ExperimentalApi("https://github.com/camunda/camunda/issues/11231")
    JobWorkerBuilderStep3 streamTimeout(final Duration timeout);

    /**
     * Sets the job worker metrics implementation to use. See {@link JobWorkerMetrics} for more.
     * Defaults to {@link JobWorkerMetrics#noop()}, an implementation which simply does nothing.
     *
     * @param metrics the implementation to use
     * @return the builder for this worker
     */
    JobWorkerBuilderStep3 metrics(final JobWorkerMetrics metrics);

    /**
     * Open the worker and start to work on available tasks.
     *
     * @return the worker
     */
    JobWorker open();
  }
}
