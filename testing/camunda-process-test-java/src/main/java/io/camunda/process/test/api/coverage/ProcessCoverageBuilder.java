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
package io.camunda.process.test.api.coverage;

import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builder class for creating configured instances of ProcessCoverage.
 *
 * <p>Provides a fluent API for setting up process coverage collection and reporting with specific
 * configurations such as excluded process definitions, report directory, and test class
 * information.
 */
public class ProcessCoverageBuilder {

  private String reportDirectory = null;
  private List<String> excludedProcessDefinitionIds = Collections.emptyList();
  private Class<?> testClass = null;
  private Supplier<CamundaDataSource> dataSourceSupplier;
  private Consumer<String> printStream;

  /**
   * Specifies process definition keys to exclude from coverage analysis.
   *
   * @param processDefinitionIds Array of process definition ids to exclude
   * @return This builder instance for method chaining
   */
  public ProcessCoverageBuilder excludeProcessDefinitionIds(final String... processDefinitionIds) {
    excludedProcessDefinitionIds = Arrays.asList(processDefinitionIds);
    return this;
  }

  /**
   * Sets the directory where coverage reports will be generated.
   *
   * @param reportDirectory Path to the directory for storing coverage reports
   * @return This builder instance for method chaining
   */
  public ProcessCoverageBuilder reportDirectory(final String reportDirectory) {
    this.reportDirectory = reportDirectory;
    return this;
  }

  /**
   * Sets the test class being executed to provide context for coverage reports.
   *
   * @param testClass Class object representing the test class
   * @return This builder instance for method chaining
   */
  public ProcessCoverageBuilder testClass(final Class<?> testClass) {
    this.testClass = testClass;
    return this;
  }

  /**
   * Sets the data source supplier for accessing Camunda process engine data.
   *
   * @param dataSourceSupplier Supplier function that provides a CamundaDataSource
   * @return This builder instance for method chaining
   */
  public ProcessCoverageBuilder dataSource(final Supplier<CamundaDataSource> dataSourceSupplier) {
    this.dataSourceSupplier = dataSourceSupplier;
    return this;
  }

  /**
   * Sets a custom print stream consumer for coverage report output.
   *
   * <p>By default, coverage summary information is printed to System.err. This method allows
   * redirecting that output to an alternative destination.
   *
   * @param printStream Consumer function that handles output strings
   * @return This builder instance for method chaining
   */
  public ProcessCoverageBuilder printStream(final Consumer<String> printStream) {
    this.printStream = printStream;
    return this;
  }

  /**
   * Builds and returns a configured ProcessCoverage instance.
   *
   * @return A new ProcessCoverage instance configured with the builder settings
   */
  public ProcessCoverage build() {
    return new ProcessCoverage(
        testClass, excludedProcessDefinitionIds, reportDirectory, printStream, dataSourceSupplier);
  }
}
