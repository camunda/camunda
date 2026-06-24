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

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobHandler;
import java.util.List;
import java.util.Map;

/**
 * Use this interface to build mock job workers in your process tests.
 *
 * <p>Example usage:
 *
 * <pre>
 *   processTestContext.mockJobWorker("myworker").thenComplete();
 * </pre>
 */
public interface JobWorkerMockBuilder {

  /** Configures the mock worker to complete jobs without any variables. */
  JobWorkerMock thenComplete();

  /**
   * Configures the mock worker to complete jobs with the specified variables.
   *
   * @param variables the variables to include when completing the job.
   */
  JobWorkerMock thenComplete(final Map<String, Object> variables);

  /**
   * Configures the mock worker to complete jobs with variables from the example data property of
   * the related BPMN elements. If no property is defined, it completes the job without variables.
   */
  JobWorkerMock thenCompleteWithExampleData();

  /**
   * Configures the mock worker to throw a BPMN error with the specified error code.
   *
   * @param errorCode the error code to throw.
   */
  JobWorkerMock thenThrowBpmnError(final String errorCode);

  /**
   * Configures the mock worker to throw a BPMN error with the specified error code and variables.
   *
   * @param errorCode the error code to throw.
   * @param variables the variables to include when throwing the error.
   */
  JobWorkerMock thenThrowBpmnError(final String errorCode, final Map<String, Object> variables);

  /**
   * Configures the mock worker to throw a BPMN error with the specified error code, error message,
   * and variables.
   *
   * @param errorCode the error code to throw.
   * @param errorMessage the error message to include when throwing the error.
   * @param variables the variables to include when throwing the error.
   */
  JobWorkerMock thenThrowBpmnError(
      final String errorCode, final String errorMessage, final Map<String, Object> variables);

  /**
   * Configures the mock worker with a custom job handler.
   *
   * @param jobHandler the custom job handler to use for processing jobs.
   */
  JobWorkerMock withHandler(final JobHandler jobHandler);

  /**
   * A JobWorkerMock is used in place of real job workers during camunda process tests. After the
   * mock worker has been invoked, you can verify the state of the activated jobs and the number of
   * invocations.
   *
   * <p>Example Usage:
   *
   * <pre>
   *   final JobWorkerMock mock = processTestContext.mockJobWorker("myworker").thenComplete();
   *
   *   // start the process
   *
   *   // Assert that the worker has been invoked and verify its state
   *   assert(mock.getInvocations()).isEqualTo(1);
   *   assertThat(mock.getActivatedJobs().get(0).getVariablesAsMap())
   *         .contains(entry("error_code", "404"));
   * </pre>
   */
  interface JobWorkerMock {
    /**
     * Gets the number of times the Job Worker was invoked.
     *
     * @return number of Job Worker invocations.
     */
    int getInvocations();

    /**
     * Gets all activated jobs every time the Job Worker was invoked.
     *
     * @return the activated jobs, or empty if there are none.
     */
    List<ActivatedJob> getActivatedJobs();
  }
}
