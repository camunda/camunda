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

import io.camunda.zeebe.client.api.ExperimentalApi;
import io.camunda.zeebe.client.api.command.CommandWithOneOrMoreTenantsStep;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import java.time.Duration;
import java.util.List;

public interface UserTaskListenerWorkerBuilderStep1 {
  /**
   * Set the type of jobs to work on.
   *
   * @param type the type of jobs (e.g. "payment")
   * @return the builder for this worker
   */
  UserTaskListenerBuilderStep2 jobType(String type);

  interface UserTaskListenerBuilderStep2 {
    UserTaskListenerBuilderStep3 eventType(String eventType);
  }

  // TODO: discuss introduction of a common high level interface for job worker and TL worker as
  // they share a lot of common configuration
  interface UserTaskListenerBuilderStep3 {
    /**
     * Set the task listener handler to process the jobs. At the end of the processing, the handler
     * should complete the job or mark it as failed;
     *
     * <p>TODO: provide an example
     *
     * <p>The task listener handler must be thread-safe.
     *
     * @param taskListener the handle to process the jobs
     * @return the builder for this worker
     */
    UserTaskListenerBuilderStep4 taskListener(UserTaskListenerHandler taskListener);
  }

  interface UserTaskListenerBuilderStep4
      extends CommandWithOneOrMoreTenantsStep<UserTaskListenerBuilderStep4> {

    UserTaskListenerBuilderStep4 timeout(long timeout);

    UserTaskListenerBuilderStep4 timeout(Duration timeout);

    UserTaskListenerBuilderStep4 name(String workerName);

    UserTaskListenerBuilderStep4 maxJobsActive(int maxJobsActive);

    UserTaskListenerBuilderStep4 pollInterval(Duration pollInterval);

    UserTaskListenerBuilderStep4 requestTimeout(Duration requestTimeout);

    UserTaskListenerBuilderStep4 fetchVariables(List<String> fetchVariables);

    UserTaskListenerBuilderStep4 fetchVariables(String... fetchVariables);

    UserTaskListenerBuilderStep4 backoffSupplier(BackoffSupplier backoffSupplier);

    @ExperimentalApi("https://github.com/camunda/camunda/issues/11231")
    UserTaskListenerBuilderStep4 streamEnabled(boolean isStreamEnabled);

    @ExperimentalApi("https://github.com/camunda/camunda/issues/11231")
    UserTaskListenerBuilderStep4 streamTimeout(final Duration timeout);

    UserTaskListenerBuilderStep4 metrics(final JobWorkerMetrics metrics);

    /**
     * Open the worker and start to work on available tasks.
     *
     * @return the worker
     */
    JobWorker open();
  }
}
