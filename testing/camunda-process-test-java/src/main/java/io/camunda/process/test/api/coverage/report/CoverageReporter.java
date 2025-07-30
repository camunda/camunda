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
package io.camunda.process.test.api.coverage.report;

import static java.util.Optional.ofNullable;

import io.camunda.process.test.api.coverage.core.CoverageCollector;
import io.camunda.process.test.api.coverage.core.CoverageCreator;
import io.camunda.process.test.api.coverage.model.Coverage;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.api.coverage.model.Suite;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
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
  public void reportCoverage(final CoverageCollector coverageCollector) {
    final Collection<Suite> suites =
        CoverageCollector.collectors().stream()
            .map(CoverageCollector::getSuite)
            .collect(Collectors.toList());
    final Collection<Model> models =
        CoverageCollector.collectors().stream()
            .flatMap(c -> c.getModels().stream())
            .distinct()
            .collect(Collectors.toList());

    writeJsonReport(
        CoverageReportCreator.createSuiteCoverageReport(
            coverageCollector.getSuite(), coverageCollector.getModels()));
    writeJsonReport(CoverageReportCreator.createAggregatedCoverageReport(suites, models));
  }

  /**
   * Prints a human-readable coverage summary to the specified output stream.
   *
   * <p>Outputs the overall coverage percentage for each process definition and provides a reference
   * to the detailed JSON report location.
   *
   * @param coverageCollector The collector containing coverage data to print
   */
  public void printCoverage(final CoverageCollector coverageCollector) {
    final Suite suite = coverageCollector.getSuite();
    final Collection<Coverage> coverages =
        CoverageCreator.aggregateCoverages(
            suite.getRuns().stream()
                .flatMap(r -> r.getCoverages().stream())
                .collect(Collectors.toList()),
            coverageCollector.getModels());
    final String message =
        "Process coverage: "
            + suite.getId()
            + "\n========================\n"
            + coverages.stream()
                .map(
                    coverage ->
                        "- "
                            + coverage.getProcessDefinitionId()
                            + ": "
                            + String.format("%.0f", coverage.getCoverage() * 100)
                            + "%")
                .collect(Collectors.joining("\n"))
            + "\n\nSee more details: "
            + resourceDirectory
            + "/"
            + suite.getId()
            + "/report.json";
    printStream.accept(message);
  }

  private void writeJsonReport(final SuiteCoverageReport report) {
    final File destFile = new File(resourceDirectory, report.getId() + "/report.json");
    writeContent(destFile, () -> CoverageReportUtil.toJson(report));
  }

  private void writeJsonReport(final AggregatedCoverageReport aggregatedReport) {
    final File jsonFile = new File(resourceDirectory, "/report.json");
    writeContent(jsonFile, () -> CoverageReportUtil.toJson(aggregatedReport));
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
