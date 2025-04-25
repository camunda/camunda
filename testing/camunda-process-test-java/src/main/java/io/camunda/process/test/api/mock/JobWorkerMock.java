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

import io.camunda.client.api.worker.JobHandler;
import java.util.Map;

/**
 * Use this interface to mock job workers in your process tests.
 *
 * <p>Example usage:
 *
 * <pre>
 *   processTestContext.mockJobWorker("myworker").thenComplete();
 * </pre>
 */
public interface JobWorkerMock {

  /** Configures the mock worker to complete jobs without any variables. */
  void thenComplete();

  /**
   * Configures the mock worker to complete jobs with the specified variables.
   *
   * @param variables the variables to include when completing the job.
   */
  void thenComplete(Map<String, Object> variables);

  /**
   * Configures the mock worker to throw a BPMN error with the specified error code.
   *
   * @param errorCode the error code to throw.
   */
  void thenThrowBpmnError(String errorCode);

  /**
   * Configures the mock worker to throw a BPMN error with the specified error code and variables.
   *
   * @param errorCode the error code to throw.
   * @param variables the variables to include when throwing the error.
   */
  void thenThrowBpmnError(String errorCode, Map<String, Object> variables);

  /**
   * Configures the mock worker with a custom job handler.
   *
   * @param jobHandler the custom job handler to use for processing jobs.
   */
  void withHandler(JobHandler jobHandler);
}
