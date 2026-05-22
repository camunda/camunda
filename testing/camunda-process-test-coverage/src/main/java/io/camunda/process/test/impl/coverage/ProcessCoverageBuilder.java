/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.coverage;

import java.util.List;
import java.util.function.Consumer;

/**
 * Builder API for creating configured instances of {@link ProcessCoverage}.
 *
 * <p>Provides a fluent API for setting up process coverage collection and reporting with specific
 * configurations such as excluded process definitions, report directory, and test class
 * information.
 */
public interface ProcessCoverageBuilder {

  /**
   * Specifies process definition keys to exclude from coverage analysis.
   *
   * @param processDefinitionIds the process definition ids to exclude
   * @return This builder instance for method chaining
   */
  ProcessCoverageBuilder excludeProcessDefinitionIds(List<String> processDefinitionIds);

  /**
   * Specifies decision definition IDs to exclude from coverage analysis.
   *
   * @param decisionDefinitionIds the decision definition IDs to exclude
   * @return This builder instance for method chaining
   */
  ProcessCoverageBuilder excludeDecisionDefinitionIds(List<String> decisionDefinitionIds);

  /**
   * Sets the directory where coverage reports will be generated.
   *
   * @param reportDirectory Path to the directory for storing coverage reports
   * @return This builder instance for method chaining
   */
  ProcessCoverageBuilder reportDirectory(String reportDirectory);

  /**
   * Sets the test class being executed to provide context for coverage reports.
   *
   * @param testClass Class object representing the test class
   * @return This builder instance for method chaining
   */
  ProcessCoverageBuilder testClass(Class<?> testClass);

  /**
   * Sets a custom print stream consumer for coverage report output.
   *
   * <p>By default, coverage summary information is printed to System.err. This method allows
   * redirecting that output to an alternative destination.
   *
   * @param printStream Consumer function that handles output strings
   * @return This builder instance for method chaining
   */
  ProcessCoverageBuilder printStream(Consumer<String> printStream);

  /**
   * Builds and returns a configured ProcessCoverage instance.
   *
   * @return A new ProcessCoverage instance configured with the builder settings
   */
  ProcessCoverage build();
}
