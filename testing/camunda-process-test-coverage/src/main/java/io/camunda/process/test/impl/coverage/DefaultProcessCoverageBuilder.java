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
package io.camunda.process.test.impl.coverage;

import io.camunda.process.test.api.coverage.ProcessCoverage;
import io.camunda.process.test.api.coverage.ProcessCoverageBuilder;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class DefaultProcessCoverageBuilder implements ProcessCoverageBuilder {

  private String reportDirectory;
  private List<String> excludedProcessDefinitionIds = Collections.emptyList();
  private List<String> excludedDecisionDefinitionIds = Collections.emptyList();
  private Class<?> testClass;
  private Consumer<String> printStream;

  @Override
  public ProcessCoverageBuilder excludeProcessDefinitionIds(
      final List<String> processDefinitionIds) {
    excludedProcessDefinitionIds = processDefinitionIds;
    return this;
  }

  @Override
  public ProcessCoverageBuilder excludeDecisionDefinitionIds(
      final List<String> decisionDefinitionIds) {
    excludedDecisionDefinitionIds = decisionDefinitionIds;
    return this;
  }

  @Override
  public ProcessCoverageBuilder reportDirectory(final String reportDirectory) {
    this.reportDirectory = reportDirectory;
    return this;
  }

  @Override
  public ProcessCoverageBuilder testClass(final Class<?> testClass) {
    this.testClass = testClass;
    return this;
  }

  @Override
  public ProcessCoverageBuilder printStream(final Consumer<String> printStream) {
    this.printStream = printStream;
    return this;
  }

  @Override
  public ProcessCoverage build() {
    return new DefaultProcessCoverage(
        testClass,
        excludedProcessDefinitionIds,
        excludedDecisionDefinitionIds,
        reportDirectory,
        printStream);
  }
}
