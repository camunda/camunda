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

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.StreamJobsResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

public interface StreamJobsCommandStep1 {
  /**
   * Set the type of jobs to work on; only jobs of this type will be activated and consumed by this
   * stream.
   *
   * @param jobType the type of jobs (e.g. "payment")
   * @return the builder for this command
   */
  StreamJobsCommandStep2 jobType(String jobType);

  interface StreamJobsCommandStep2 {

    /**
     * Sets the consumer to receive activated jobs. Note that jobs can be activated on different
     * threads, so the consumer should be thread-safe.
     *
     * @param consumer the job consumer
     * @return the builder's next step
     * @throws NullPointerException if the consumer is null
     */
    StreamJobsCommandStep3 consumer(final Consumer<ActivatedJob> consumer);
  }

  interface StreamJobsCommandStep3
      extends CommandWithOneOrMoreTenantsStep<StreamJobsCommandStep3>,
          FinalCommandStep<StreamJobsResponse> {
    /**
     * Set the time for how long a job is exclusively assigned for this subscription.
     *
     * <p>In this time, the job can not be assigned by other subscriptions to ensure that only one
     * subscription work on the job. When the time is over then the job can be assigned again by
     * this or other subscription if it's not completed yet.
     *
     * <p>If no time is set then the default is used from the configuration.
     *
     * @param timeout the time as duration (e.g. "Duration.ofMinutes(5)")
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    StreamJobsCommandStep3 timeout(Duration timeout);

    /**
     * Set the name of the job worker.
     *
     * <p>This name is used to identify the worker which activated the jobs. Its main purpose is for
     * monitoring and auditing. Commands on activated jobs do not check the worker name, i.e.
     * complete or fail job.
     *
     * <p>If no name is set then the default is used from the configuration.
     *
     * @param workerName the name of the worker (e.g. "payment-service")
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    StreamJobsCommandStep3 workerName(String workerName);

    /**
     * Set a list of variable names which should be fetch on job activation.
     *
     * <p>The jobs which are activated by this command will only contain variables from this list.
     *
     * <p>This can be used to limit the number of variables of the activated jobs.
     *
     * @param fetchVariables list of variables names to fetch on activation
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    StreamJobsCommandStep3 fetchVariables(List<String> fetchVariables);

    /**
     * Set a list of variable names which should be fetched on job activation.
     *
     * <p>The jobs which are activated by this command will only contain variables from this list.
     *
     * <p>This can be used to limit the number of variables of the activated jobs.
     *
     * @param fetchVariables list of variables names to fetch on activation
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    StreamJobsCommandStep3 fetchVariables(String... fetchVariables);
  }
}
