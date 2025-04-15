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
package io.camunda.process.test.api.mock;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobHandler;
import java.util.HashMap;
import java.util.Map;

public class JobWorkerMock {

  private final String jobId;
  private final CamundaClient client;

  /**
   * Constructs a `JobWorkerMock` instance.
   *
   * @param jobId the job type to mock, matching the `zeebeJobType` in the BPMN model.
   * @param client the Camunda client used to create the mock worker.
   */
  public JobWorkerMock(final String jobId, final CamundaClient client) {
    this.jobId = jobId;
    this.client = client;
  }

  /** Configures the mock worker to complete jobs without any variables. */
  public void thenComplete() {
    thenComplete(new HashMap<>());
  }

  /**
   * Configures the mock worker to complete jobs with the specified variables.
   *
   * @param variables the variables to include when completing the job.
   */
  public void thenComplete(final Map<String, Object> variables) {
    withHandler(
        (jobClient, job) -> {
          jobClient.newCompleteCommand(job).variables(variables).send().join();
        });
  }

  /**
   * Configures the mock worker to throw a BPMN error with the specified error code.
   *
   * @param errorCode the error code to throw.
   */
  public void thenThrowBpmnError(final String errorCode) {
    thenThrowBpmnError(errorCode, new HashMap<>());
  }

  /**
   * Configures the mock worker to throw a BPMN error with the specified error code and variables.
   *
   * @param errorCode the error code to throw.
   * @param variables the variables to include when throwing the error.
   */
  public void thenThrowBpmnError(final String errorCode, final Map<String, Object> variables) {
    withHandler(
        (jobClient, job) -> {
          jobClient
              .newThrowErrorCommand(job)
              .errorCode(errorCode)
              .variables(variables)
              .send()
              .join();
        });
  }

  /**
   * Configures the mock worker with a custom job handler.
   *
   * @param jobHandler the custom job handler to use for processing jobs.
   */
  public void withHandler(final JobHandler jobHandler) {
    client.newWorker().jobType(jobId).handler(jobHandler).open();
  }
}
