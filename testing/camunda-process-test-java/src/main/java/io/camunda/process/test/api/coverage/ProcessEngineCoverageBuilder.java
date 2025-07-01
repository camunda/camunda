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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.assertj.core.api.Condition;

public class ProcessEngineCoverageBuilder {

  public static final String DEFAULT_ASSERT_AT_LEAST_PROPERTY =
      "org.camunda.process_test_coverage.ASSERT_AT_LEAST";

  private boolean detailedCoverageLogging = false;
  private boolean handleTestMethodCoverage = true;
  private Double coverageAtLeast = null;
  private String coverageAssertAtLeastProperty = DEFAULT_ASSERT_AT_LEAST_PROPERTY;
  private String reportDirectory = null;
  private List<String> excludedProcessDefinitionKeys = Collections.emptyList();
  private final List<Condition<Double>> classCoverageAssertionConditions = new ArrayList<>();
  private final Map<String, List<Condition<Double>>> testMethodNameToCoverageConditions =
      new ConcurrentHashMap<>();

  public ProcessEngineCoverageBuilder withDetailedCoverageLogging() {
    detailedCoverageLogging = true;
    return this;
  }

  public ProcessEngineCoverageBuilder handleTestMethodCoverage(
      final boolean handleTestMethodCoverage) {
    this.handleTestMethodCoverage = handleTestMethodCoverage;
    return this;
  }

  public ProcessEngineCoverageBuilder coverageClassAssertAtLeast(final double percentage) {
    coverageAtLeast = validatePercentage(percentage);
    return this;
  }

  public ProcessEngineCoverageBuilder excludeProcessDefinitionKeys(
      final String... processDefinitionKeys) {
    excludedProcessDefinitionKeys = Arrays.asList(processDefinitionKeys);
    return this;
  }

  public ProcessEngineCoverageBuilder coverageAssertAtLeastProperty(final String property) {
    coverageAssertAtLeastProperty = property;
    return this;
  }

  public ProcessEngineCoverageBuilder reportDirectory(final String reportDirectory) {
    this.reportDirectory = reportDirectory;
    return this;
  }

  private Double coverageFromSystemProperty(final String key) {
    final String property = System.getProperty(key);
    if (property != null) {
      try {
        return validatePercentage(Double.parseDouble(property));
      } catch (final NumberFormatException e) {
        throw new RuntimeException(
            "BAD TEST CONFIGURATION: system property \"" + key + "\" must be double", e);
      }
    }
    return null;
  }

  private Double validatePercentage(final double percentage) {
    if (percentage < 0 || percentage > 1) {
      throw new RuntimeException(
          "BAD TEST CONFIGURATION: coverageAtLeast "
              + percentage
              + " ("
              + (100 * percentage)
              + "%)");
    }
    return percentage;
  }

  private void addTestMethodCoverageCondition(
      final String methodName, final Condition<Double> condition) {
    testMethodNameToCoverageConditions
        .computeIfAbsent(methodName, k -> new ArrayList<>())
        .add(condition);
  }

  private void addClassCoverageAtLeast(final double percentage) {
    classCoverageAssertionConditions.add(
        new Condition<>(
            p -> p >= percentage, "matches if the coverage ratio is at least " + percentage));
  }

  public ProcessEngineCoverage build() {
    Optional.ofNullable(coverageFromSystemProperty(coverageAssertAtLeastProperty))
        .ifPresent(this::addClassCoverageAtLeast);
    Optional.ofNullable(coverageAtLeast).ifPresent(this::addClassCoverageAtLeast);

    return new ProcessEngineCoverage(
        detailedCoverageLogging,
        handleTestMethodCoverage,
        excludedProcessDefinitionKeys,
        classCoverageAssertionConditions,
        testMethodNameToCoverageConditions,
        reportDirectory);
  }
}
