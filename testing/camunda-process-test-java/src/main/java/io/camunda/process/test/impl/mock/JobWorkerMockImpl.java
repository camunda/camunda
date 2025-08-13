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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerMockImpl implements JobWorkerMock {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerMockImpl.class);

  private final List<ActivatedJob> activatedJobs = new ArrayList<>();

  public JobWorkerMockImpl(
      final String jobType, final CamundaClient camundaClient, final JobHandler jobHandler) {

    final JobHandler loggingJobHandler =
        (jobClient, job) -> {
          LOGGER.debug(
              "Mock: Pass job to custom handler [job-type: '{}', job-key: '{}']",
              jobType,
              job.getKey());

          activatedJobs.add(job);
          jobHandler.handle(jobClient, job);
        };

    final JobHandler safeLoggingJobHandler =
        (client, job) -> {
          try {
            loggingJobHandler.handle(client, job);
          } catch (final AssertionError e) {
            final String failureMessage =
                String.format(
                    "JobWorkerMock [job-type: %s, job-key: %s] has failed assertions and will be terminated.",
                    jobType, job.getKey());
            System.err.println(failureMessage);
            e.printStackTrace();

            client.newFailCommand(job.getKey()).retries(0).send().join();
          }
        };

    camundaClient.newWorker().jobType(jobType).handler(safeLoggingJobHandler).open();
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
