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
package io.camunda.process.test.impl.mock;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import java.util.ArrayList;
import java.util.List;
import io.camunda.process.test.api.mock.JobWorkerMock;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerMockImpl implements JobWorkerMock {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerMockImpl.class);

  private final String jobType;
  private final CamundaClient client;
  private final PrintStream printStream;
  private final List<ActivatedJob> activatedJobs = new ArrayList<>();

  /**
   * Constructs a `JobWorkerMock` instance.
   *
   * @param jobType the job type to mock, matching the `zeebeJobType` in the BPMN model.
   * @param client the Camunda client used to create the mock worker.
   */
  public JobWorkerMockImpl(final String jobType, final CamundaClient client) {

    this(jobType, client, System.err);
  }

  public JobWorkerMockImpl(
      final String jobType, final CamundaClient client, final PrintStream printStream, final JobHandler jobHandler) {
    this.jobType = jobType;
    this.client = client;
    this.printStream = printStream;
  }

    final JobHandler loggingJobHandler =
        (jobClient, job) -> {
          LOGGER.debug(
              "Mock: Pass job to custom handler [job-type: '{}', job-key: '{}']",
              jobType,
              job.getKey());

          activatedJobs.add(job);
          jobHandler.handle(jobClient, job);
        };

    camundaClient.newWorker().jobType(jobType).handler(loggingJobHandler).open();
  }

  @Override
  public void withHandler(final JobHandler jobHandler) {
    final JobHandler loggingJobHandler =
        (client, job) -> {
          LOGGER.debug(
              "Mock: Pass job to custom handler [job-type: '{}', job-key: '{}']",
              jobType,
              job.getKey());

          jobHandler.handle(client, job);
        };

    final JobHandler safeLoggingJobHandler =
        (client, job) -> {
          try {
            loggingJobHandler.handle(client, job);
          } catch (final AssertionError e) {
            LOGGER.error("JobWorkerMock has a failed assertion and will be terminated.", e);

            client.newFailCommand(job.getKey()).retries(0).send().join();
          }
        };

    client.newWorker().jobType(jobType).handler(safeLoggingJobHandler).open();
  }

  @Override
  public int getInvocations() {
    return activatedJobs.size();
  }

  @Override
  public List<ActivatedJob> getActivatedJobs() {
    return activatedJobs;
  }
}
