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

import java.util.Map;
import java.util.function.Function;

/**
 * Use this interface to build mock child processes in your process tests.
 *
 * <p>Example usage:
 *
 * <pre>
 *   // Simple mock without version tag
 *   processTestContext.mockChildProcess().withProcessId("my-child-process").thenComplete();
 *
 *   // Mock with a version tag (for call activities using bindingType="versionTag")
 *   processTestContext
 *       .mockChildProcess()
 *       .withProcessId("my-child-process")
 *       .withVersionTag("1.7.1")
 *       .thenComplete();
 *
 *   // Mock with a version tag and output variables
 *   processTestContext
 *       .mockChildProcess()
 *       .withProcessId("my-child-process")
 *       .withVersionTag("1.7.1")
 *       .thenComplete(Map.of("result", "ok"));
 *
 *   // Mock with output variables derived from the parent process variables
 *   processTestContext
 *       .mockChildProcess()
 *       .withProcessId("my-child-process")
 *       .thenComplete(parentVars -> Map.of("result", parentVars.get("input")));
 * </pre>
 */
public interface MockChildProcessBuilder {

  /**
   * Sets the process definition ID of the child process to mock.
   *
   * @param processId the ID of the child process to mock
   * @return this builder
   */
  MockChildProcessBuilder withProcessId(String processId);

  /**
   * Sets the version tag for the mocked child process. This is required when the call activity uses
   * {@code bindingType="versionTag"}.
   *
   * @param versionTag the version tag to assign to the deployed stub process
   * @return this builder
   */
  MockChildProcessBuilder withVersionTag(String versionTag);

  /** Deploys the mocked child process without any output variables. */
  void thenComplete();

  /**
   * Deploys the mocked child process and sets the provided variables as output.
   *
   * @param variables a map of variables to set for the mocked child process
   */
  void thenComplete(Map<String, Object> variables);

  /**
   * Deploys the mocked child process and sets the output variables by applying the given function
   * to the parent process instance variables. This allows dynamically determining the child process
   * output based on the parent process state at the time the child process is called.
   *
   * @param variablesSupplier a function that receives the parent process variables and returns the
   *     variables to set as output for the mocked child process
   */
  void thenComplete(Function<Map<String, Object>, Map<String, Object>> variablesSupplier);
}
