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
package io.camunda.process.test.impl.coverage.report;

import static java.util.Optional.ofNullable;

import io.camunda.process.test.api.coverage.model.CoverageReport;
import io.camunda.process.test.api.coverage.model.DecisionCoverage;
import io.camunda.process.test.api.coverage.model.DecisionModel;
import io.camunda.process.test.api.coverage.model.ProcessCoverage;
import io.camunda.process.test.api.coverage.model.ProcessModel;
import io.camunda.process.test.api.coverage.model.Suite;
import io.camunda.process.test.impl.coverage.core.CoverageCollector;
import io.camunda.process.test.impl.coverage.core.CoverageCreator;
import io.camunda.process.test.impl.coverage.core.DecisionCoverageCreator;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates and writes process coverage reports from collected coverage data.
 *
 * <p>This class is responsible for creating, storing, and displaying coverage reports in various
 * formats (JSON files and console output). It handles both individual test suite reports and
 * aggregated reports that combine results from multiple test runs.
 *
 * <p>Reports are written to the filesystem in a configurable directory structure and can also be
 * output to the console in a human-readable format.
 */
public class CoverageReporter {

  /** Root directory for all coverage reports. */
  public static final String TARGET_DIR_ROOT =
      System.getProperty(
          "camunda-process-test-coverage.target-dir-root", "target/process-test-coverage/");

  private static final Logger LOG = LoggerFactory.getLogger(CoverageReporter.class);
  private final Consumer<String> printStream;
  private final File resourceDirectory;

  /**
   * Creates a new coverage reporter with the specified report directory and output stream.
   *
   * <p>The reporter will write JSON files to the given directory and print summary information
   * through the provided print stream consumer.
   *
   * @param resourceDirectory Path to the directory where coverage reports should be stored
   *     (defaults to "target/process-test-coverage/" if null)
   * @param printStream Consumer function that handles output strings (defaults to
   *     System.err::println if null)
   */
  public CoverageReporter(final String resourceDirectory, final Consumer<String> printStream) {
    this.printStream = Optional.ofNullable(printStream).orElse(LOG::info);
    this.resourceDirectory =
        new File(ofNullable(resourceDirectory).orElse(TARGET_DIR_ROOT)).getAbsoluteFile();
  }

  /**
   * Reports coverage data by writing it to JSON files.
   *
   * <p>Creates both a suite-specific report and updates the aggregated report that combines all
   * test runs.
   *
   * @param coverageCollector The collector containing coverage data to report
   */
  public CoverageReport reportCoverage(final CoverageCollector coverageCollector) {
    final Collection<Suite> suites =
        CoverageCollector.collectors().stream()
            .map(CoverageCollector::getSuite)
            .collect(Collectors.toList());
    final Collection<ProcessModel> processModels =
        CoverageCollector.collectors().stream()
            .flatMap(c -> c.getModels().stream())
            .distinct()
            .collect(Collectors.toList());
    final Collection<DecisionModel> decisionModels =
        CoverageCollector.collectors().stream()
            .flatMap(c -> c.getDecisionModels().stream())
            .distinct()
            .collect(Collectors.toList());

    final CoverageReport suiteReport =
        CoverageReportCreator.createAggregatedCoverageReport(
            java.util.Collections.singletonList(coverageCollector.getSuite()),
            coverageCollector.getModels(),
            coverageCollector.getDecisionModels());
    final CoverageReport aggregatedReport =
        CoverageReportCreator.createAggregatedCoverageReport(suites, processModels, decisionModels);

    writeJsonReport(coverageCollector.getSuite().getId(), suiteReport);
    writeJsonReport(aggregatedReport);
    writeHtmlReport(aggregatedReport);
    printCoverage(coverageCollector);
    return aggregatedReport;
  }

  /**
   * Prints a human-readable coverage summary to the specified output stream.
   *
   * <p>Outputs the overall coverage percentage for each process definition and provides a reference
   * to the detailed JSON report location.
   *
   * @param coverageCollector The collector containing coverage data to print
   */
  public CoverageReport createSuiteCoverageReport(final CoverageCollector coverageCollector) {
    return CoverageReportCreator.createAggregatedCoverageReport(
        java.util.Collections.singletonList(coverageCollector.getSuite()),
        coverageCollector.getModels(),
        coverageCollector.getDecisionModels());
  }

  private void printCoverage(final CoverageCollector coverageCollector) {
    final Suite suite = coverageCollector.getSuite();
    final Collection<ProcessCoverage> coverages =
        CoverageCreator.aggregateCoverages(
            suite.getRuns().stream()
                .flatMap(r -> r.getProcessCoverages().stream())
                .collect(Collectors.toList()),
            coverageCollector.getModels());
    final String processCoverageList =
        coverages.stream()
            .map(
                coverage ->
                    String.format(
                        "- %s: %.0f%%",
                        coverage.getProcessDefinitionId(), coverage.getCoverage() * 100))
            .collect(Collectors.joining("\n"));

    final Collection<DecisionCoverage> decisionCoverages =
        DecisionCoverageCreator.aggregateCoverages(
            suite.getRuns().stream()
                .flatMap(r -> r.getDecisionCoverages().stream())
                .collect(Collectors.toList()),
            coverageCollector.getDecisionModels());
    final String decisionCoverageList =
        decisionCoverages.stream()
            .map(
                dc ->
                    String.format(
                        "- %s: %.0f%%", dc.getDecisionDefinitionId(), dc.getCoverage() * 100))
            .collect(Collectors.joining("\n"));

    final StringBuilder coverageText = new StringBuilder();
    if (!processCoverageList.isEmpty()) {
      coverageText.append("Process coverage:\n").append(processCoverageList);
    }
    if (!decisionCoverageList.isEmpty()) {
      if (coverageText.length() > 0) {
        coverageText.append("\n\n");
      }
      coverageText.append("Decision coverage:\n").append(decisionCoverageList);
    }

    final String message =
        MessageFormat.format(
            "Coverage: {1} \n"
                + "========================\n"
                + "{2}\n\n"
                + " Coverage report: file://{0}/report.html\n",
            resourceDirectory, suite.getId(), coverageText.toString());
    printStream.accept(message);
  }

  private void writeJsonReport(final String suiteId, final CoverageReport report) {
    final File destFile = new File(resourceDirectory, suiteId + "/report.json");
    writeContent(destFile, () -> CoverageReportUtil.toJson(report));
  }

  private void writeJsonReport(final CoverageReport aggregatedReport) {
    final File jsonFile = new File(resourceDirectory, "/report.json");
    writeContent(jsonFile, () -> CoverageReportUtil.toJson(aggregatedReport));
  }

  private void writeHtmlReport(final CoverageReport coverageReport) {
    CoverageReportUtil.installReportDependencies(resourceDirectory);
    final File destFile = new File(resourceDirectory, "report.html");
    writeContent(destFile, () -> CoverageReportUtil.toHtml(coverageReport));
  }

  private void writeContent(final File destFile, final Supplier<String> contentProvider) {
    try {
      if (destFile.getParentFile() != null && !destFile.getParentFile().exists()) {
        Files.createDirectories(destFile.getParentFile().toPath());
      }
      Files.write(
          FileSystems.getDefault().getPath(destFile.getPath()),
          contentProvider.get().getBytes(StandardCharsets.UTF_8));
    } catch (final IOException ex) {
      throw new RuntimeException("Unable to write report.", ex);
    }
  }
}
