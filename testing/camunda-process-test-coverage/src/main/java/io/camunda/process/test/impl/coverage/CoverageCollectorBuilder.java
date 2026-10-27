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

import java.util.List;
import java.util.function.Consumer;

/**
 * Builder API for creating configured instances of {@link CoverageCollector}.
 *
 * <p>Provides a fluent API for setting up process coverage collection and reporting with specific
 * configurations such as excluded process definitions, report directory, and test class
 * information.
 */
public interface CoverageCollectorBuilder {

  /**
   * Specifies process definition keys to exclude from coverage analysis.
   *
   * @param processDefinitionIds the process definition ids to exclude
   * @return This builder instance for method chaining
   */
  CoverageCollectorBuilder excludeProcessDefinitionIds(List<String> processDefinitionIds);

  /**
   * Specifies decision definition IDs to exclude from coverage analysis.
   *
   * @param decisionDefinitionIds the decision definition IDs to exclude
   * @return This builder instance for method chaining
   */
  CoverageCollectorBuilder excludeDecisionDefinitionIds(List<String> decisionDefinitionIds);

  /**
   * Sets the directory where coverage reports will be generated.
   *
   * @param reportDirectory Path to the directory for storing coverage reports
   * @return This builder instance for method chaining
   */
  CoverageCollectorBuilder reportDirectory(String reportDirectory);

  /**
   * Sets a custom print stream consumer for coverage report output.
   *
   * <p>By default, coverage summary information is printed to System.err. This method allows
   * redirecting that output to an alternative destination.
   *
   * @param printStream Consumer function that handles output strings
   * @return This builder instance for method chaining
   */
  CoverageCollectorBuilder printStream(Consumer<String> printStream);

  /**
   * Builds and returns a configured ProcessCoverage instance.
   *
   * @return A new ProcessCoverage instance configured with the builder settings
   */
  CoverageCollector build();
}
