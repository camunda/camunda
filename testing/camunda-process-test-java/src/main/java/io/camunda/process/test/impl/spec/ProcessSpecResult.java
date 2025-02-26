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

import java.time.Duration;
import java.util.List;

public class ProcessSpecResult {

  private int passesTestCases;
  private int totalTestCases;
  private Duration totalTestDuration;
  private List<SpecTestCaseResult> testResults;

  public ProcessSpecResult(
      final int passesTestCases,
      final int totalTestCases,
      final Duration totalTestDuration,
      final List<SpecTestCaseResult> testResults) {
    this.passesTestCases = passesTestCases;
    this.totalTestCases = totalTestCases;
    this.totalTestDuration = totalTestDuration;
    this.testResults = testResults;
  }

  public ProcessSpecResult() {}

  public List<SpecTestCaseResult> getTestResults() {
    return testResults;
  }

  public void setTestResults(final List<SpecTestCaseResult> testResults) {
    this.testResults = testResults;
  }

  public int getPassesTestCases() {
    return passesTestCases;
  }

  public void setPassesTestCases(final int passesTestCases) {
    this.passesTestCases = passesTestCases;
  }

  public int getTotalTestCases() {
    return totalTestCases;
  }

  public void setTotalTestCases(final int totalTestCases) {
    this.totalTestCases = totalTestCases;
  }

  public Duration getTotalTestDuration() {
    return totalTestDuration;
  }

  public void setTotalTestDuration(final Duration totalTestDuration) {
    this.totalTestDuration = totalTestDuration;
  }
}
