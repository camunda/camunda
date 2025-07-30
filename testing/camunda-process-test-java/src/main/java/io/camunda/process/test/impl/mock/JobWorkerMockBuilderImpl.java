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
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerMockBuilderImpl implements JobWorkerMockBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerMockBuilderImpl.class);

  private final String jobType;
  private final CamundaClient client;

  /**
   * Constructs a `JobWorkerMockBuilder` instance.
   *
   * @param jobType the job type to mock, matching the `zeebeJobType` in the BPMN model.
   * @param client the Camunda client used to create the mock worker.
   */
  public JobWorkerMockBuilderImpl(final String jobType, final CamundaClient client) {
    this.jobType = jobType;
    this.client = client;
  }

  @Override
  public JobWorkerMock thenComplete() {
    return thenComplete(new HashMap<>());
  }

  @Override
  public JobWorkerMock thenComplete(final Map<String, Object> variables) {
    return withHandler(
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
  public JobWorkerMock thenThrowBpmnError(final String errorCode) {
    return thenThrowBpmnError(errorCode, new HashMap<>());
  }

  @Override
  public JobWorkerMock thenThrowBpmnError(
      final String errorCode, final Map<String, Object> variables) {
    return withHandler(
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
  public JobWorkerMock withHandler(final JobHandler jobHandler) {
    return new JobWorkerMockImpl(jobType, client, jobHandler);
  }
}
