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
package io.camunda.process.test.impl.spec;

import io.camunda.process.test.impl.spec.dsl.SpecInstruction;
import io.camunda.process.test.impl.testresult.ProcessInstanceResult;
import java.time.Duration;
import java.util.List;

public class SpecTestCaseResult {

  private String name;
  private boolean success;
  private SpecInstruction failedInstruction;
  private String failureMessage;
  private Duration testDuration;
  private List<ProcessInstanceResult> testOutput;

  public SpecTestCaseResult() {}

  public SpecTestCaseResult(
      final String name,
      final boolean success,
      final SpecInstruction failedInstruction,
      final String failureMessage,
      final Duration testDuration,
      final List<ProcessInstanceResult> testOutput) {
    this.name = name;
    this.success = success;
    this.failedInstruction = failedInstruction;
    this.failureMessage = failureMessage;
    this.testDuration = testDuration;
    this.testOutput = testOutput;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public void setFailureMessage(final String failureMessage) {
    this.failureMessage = failureMessage;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(final boolean success) {
    this.success = success;
  }

  public Duration getTestDuration() {
    return testDuration;
  }

  public void setTestDuration(final Duration testDuration) {
    this.testDuration = testDuration;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public SpecInstruction getFailedInstruction() {
    return failedInstruction;
  }

  public void setFailedInstruction(final SpecInstruction failedInstruction) {
    this.failedInstruction = failedInstruction;
  }

  public List<ProcessInstanceResult> getTestOutput() {
    return testOutput;
  }

  public void setTestOutput(final List<ProcessInstanceResult> testOutput) {
    this.testOutput = testOutput;
  }
}
