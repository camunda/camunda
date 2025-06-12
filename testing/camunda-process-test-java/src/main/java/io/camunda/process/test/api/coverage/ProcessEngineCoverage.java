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

import io.camunda.process.test.api.coverage.core.CoverageCollector;
import io.camunda.process.test.api.coverage.core.EventCreator;
import io.camunda.process.test.api.coverage.core.ExclusionUtils;
import io.camunda.process.test.api.coverage.model.Run;
import io.camunda.process.test.api.coverage.model.Suite;
import io.camunda.process.test.api.coverage.report.CoverageReportUtil;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ProcessEngineCoverage {

  private static final Logger logger = Logger.getLogger(ProcessEngineCoverage.class.getName());

  private final CoverageCollector coverageCollector;
  private final boolean detailedCoverageLogging;
  private final boolean handleTestMethodCoverage;
  private final List<String> excludedProcessDefinitionKeys;
  private final List<Condition<Double>> classCoverageAssertionConditions;
  private final Map<String, List<Condition<Double>>> testMethodNameToCoverageConditions;
  private final String reportDirectory;
  private boolean suiteInitialized = false;
  private String activeSuiteContextId;
  private Supplier<CamundaDataSource> dataSourceSupplier;

  public ProcessEngineCoverage(
      final boolean detailedCoverageLogging,
      final boolean handleTestMethodCoverage,
      final List<String> excludedProcessDefinitionKeys,
      final List<Condition<Double>> classCoverageAssertionConditions,
      final Map<String, List<Condition<Double>>> testMethodNameToCoverageConditions,
      final String reportDirectory) {
    this.detailedCoverageLogging = detailedCoverageLogging;
    this.handleTestMethodCoverage = handleTestMethodCoverage;
    this.excludedProcessDefinitionKeys = excludedProcessDefinitionKeys;
    this.classCoverageAssertionConditions = classCoverageAssertionConditions;
    this.testMethodNameToCoverageConditions = testMethodNameToCoverageConditions;
    this.reportDirectory = reportDirectory;
    coverageCollector = new CoverageCollector();
  }

  public static ProcessEngineCoverageBuilder newBuilder() {
    return new ProcessEngineCoverageBuilder();
  }

  public void setDataSourceSupplier(final Supplier<CamundaDataSource> dataSourceSupplier) {
    this.dataSourceSupplier = dataSourceSupplier;
  }

  public void beginTestSuiteCoverage(final ExtensionContext context) {
    if (!ExclusionUtils.isTestClassExcluded(context)
        && (!suiteInitialized
            || (!context.getUniqueId().equals(activeSuiteContextId) && !isNested(context)))) {
      initializeSuite(context, context.getDisplayName());
    }
  }

  public void endTestSuiteCoverage(final ExtensionContext context) {
    if (!ExclusionUtils.isTestClassExcluded(context)
        && context.getUniqueId().equals(activeSuiteContextId)) {
      final Suite suite = coverageCollector.getActiveSuite();
      final double suiteCoveragePercentage = suite.calculateCoverage(coverageCollector.getModels());

      if (Double.isNaN(suiteCoveragePercentage)) {
        logger.warning(
            suite.getName() + " test class coverage could not be calculated, check configuration");
      } else {
        logger.info(suite.getName() + " test class coverage is: " + suiteCoveragePercentage);
        logCoverageDetail(suite);

        CoverageReportUtil.createReport(coverageCollector, reportDirectory);
        CoverageReportUtil.createJsonReport(coverageCollector, reportDirectory);

        assertCoverage(suiteCoveragePercentage, classCoverageAssertionConditions);
      }
    }
  }

  public void beginTestMethodCoverage(final ExtensionContext context) {
    if (!ExclusionUtils.isTestMethodExcluded(context)) {
      final String runId = context.getUniqueId();
      coverageCollector.addRun(runId, context.getDisplayName());
      coverageCollector.activateRun(runId);
    }
  }

  public void endTestMethodCoverage(final ExtensionContext context) {
    if (!ExclusionUtils.isTestMethodExcluded(context)) {
      if (dataSourceSupplier == null) {
        throw new IllegalStateException("DataSource supplier is not set");
      }
      final CamundaDataSource dataSource = dataSourceSupplier.get();
      EventCreator.createEvents(dataSource).forEach(e -> coverageCollector.addEvent(e, dataSource));
      if (handleTestMethodCoverage) {
        handleTestMethodCoverage(context);
      }
    }
  }

  private boolean isNested(final ExtensionContext context) {
    return context
        .getParent()
        .map(parent -> parent.getUniqueId().equals(activeSuiteContextId))
        .orElse(false);
  }

  private void initializeSuite(final ExtensionContext context, final String name) {
    final String suiteId = context.getRequiredTestClass().getName();
    coverageCollector.addSuite(suiteId, name);
    coverageCollector.setExcludedProcessDefinitionKeys(excludedProcessDefinitionKeys);
    coverageCollector.activateSuite(suiteId);
    activeSuiteContextId = context.getUniqueId();
    suiteInitialized = true;
  }

  private void logCoverageDetail(final Suite suite) {
    if (detailedCoverageLogging) {
      logger.info(suite.toString());
    }
  }

  private void logCoverageDetail(final Run run) {
    if (detailedCoverageLogging) {
      logger.info(run.toString());
    }
  }

  private void assertCoverage(final double coverage, final List<Condition<Double>> conditions) {
    for (final Condition<Double> condition : conditions) {
      Assertions.assertThat(coverage).satisfies(condition);
    }
  }

  private void handleTestMethodCoverage(final ExtensionContext context) {
    final Suite suite = coverageCollector.getActiveSuite();
    final Run run = suite.getRun(context.getUniqueId());
    if (run == null) {
      return;
    }
    final double coveragePercentage = run.calculateCoverage(coverageCollector.getModels());

    if (Double.isNaN(coveragePercentage)) {
      logger.warning(
          run.getName() + " test method coverage could not be calculated, check configuration");
    } else {
      logger.info(run.getName() + " test method coverage is " + coveragePercentage);
      logCoverageDetail(run);

      final List<Condition<Double>> conditions =
          testMethodNameToCoverageConditions.get(run.getName());
      if (conditions != null) {
        assertCoverage(coveragePercentage, conditions);
      }
    }
  }
}
