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
import io.camunda.client.api.worker.JobHandler;
import io.camunda.process.test.api.mock.JobWorkerMock;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerMockImpl implements JobWorkerMock {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerMockImpl.class);

  private final String jobType;
  private final CamundaClient client;

  /**
   * Constructs a `JobWorkerMock` instance.
   *
   * @param jobType the job type to mock, matching the `zeebeJobType` in the BPMN model.
   * @param client the Camunda client used to create the mock worker.
   */
  public JobWorkerMockImpl(final String jobType, final CamundaClient client) {
    this.jobType = jobType;
    this.client = client;
  }

  @Override
  public void thenComplete() {
    thenComplete(new HashMap<>());
  }

  @Override
  public void thenComplete(final Map<String, Object> variables) {
    withHandler(
        (jobClient, job) -> {
          LOGGER.debug(
              "Mock: Complete job with variables {} [job-type: '{}', job-key: '{}']",
              variables,
              jobType,
              job.getKey());
          jobClient.newCompleteCommand(job).variables(variables).send().join();
        });
  }

  @Override
  public void thenThrowBpmnError(final String errorCode) {
    thenThrowBpmnError(errorCode, new HashMap<>());
  }

  @Override
  public void thenThrowBpmnError(final String errorCode, final Map<String, Object> variables) {
    withHandler(
        (jobClient, job) -> {
          LOGGER.debug(
              "Mock: Throw BPMN error with error code {} and variables {} [job-type: '{}', job-key: '{}']",
              errorCode,
              variables,
              jobType,
              job.getKey());
          jobClient
              .newThrowErrorCommand(job)
              .errorCode(errorCode)
              .variables(variables)
              .send()
              .join();
        });
  }

  @Override
  public void withHandler(final JobHandler jobHandler) {
    final JobHandler loggingJobHandler = (client, job) -> {
      LOGGER.debug(
          "Mock: Pass job to custom handler [job-type: '{}', job-key: '{}']",
          jobType,
          job.getKey());

      jobHandler.handle(client, job);
    };

    client.newWorker().jobType(jobType).handler(loggingJobHandler).open();
  }
    client
        .newWorker()
        .jobType(jobType)
        .handler(
            (jobClient, job) -> {
              LOGGER.debug(
                  "Mock: Pass job to custom handler [job-type: '{}', job-key: '{}']",
                  jobType,
                  job.getKey());
              jobHandler.handle(jobClient, job);
            })
        .open();
  }
}
