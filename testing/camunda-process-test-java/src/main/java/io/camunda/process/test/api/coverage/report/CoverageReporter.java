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
    writeJsonReport(coverageCollector.getSuite(), coverageCollector.getModels());
    updateAndWriteAggregatedJsonReport(coverageCollector.getSuite(), coverageCollector.getModels());
  }

  /**
   * Writes a JSON report file for a specific test suite.
   *
   * <p>Creates a suite-specific JSON report containing coverage data for all processes executed
   * during the test suite.
   *
   * @param suite The test suite containing coverage information
   * @param models Collection of process models for coverage calculation
   */
  private void writeJsonReport(final Suite suite, final Collection<Model> models) {
    final File destFile = new File(resourceDirectory, suite.getId() + "/report.json");
    writeContent(
        destFile, () -> CoverageReportUtil.toJson(CoverageReportCreator.create(suite, models)));
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
    final CoverageReport report =
        CoverageReportCreator.create(suite, coverageCollector.getModels());
    printStream.accept("Process coverage: " + suite.getId());
    printStream.accept("========================");
    for (final Coverage coverage : report.getCoverages()) {
      printStream.accept(
          "- "
              + coverage.getProcessDefinitionId()
              + ": "
              + String.format("%.0f", coverage.getCoverage() * 100)
              + "%");
    }
    printStream.accept(
        "\nSee more details: " + resourceDirectory + "/" + suite.getId() + "/report.json");
  }

  /**
   * Updates and writes the aggregated coverage report.
   *
   * <p>Reads the existing aggregated report (if available), merges it with new coverage data, and
   * writes the updated report to the file system. This maintains a consolidated view of coverage
   * across multiple test suites.
   *
   * @param suite The test suite containing new coverage information to aggregate
   * @param models Collection of process models for coverage calculation
   */
  private void updateAndWriteAggregatedJsonReport(
      final Suite suite, final Collection<Model> models) {
    final File jsonFile = new File(resourceDirectory, "/report.json");
    final CoverageReport oldReport =
        jsonFile.exists() ? CoverageReportUtil.fromJsonFile(jsonFile) : new CoverageReport();
    final CoverageReport aggregatedReport =
        CoverageReportCreator.aggregatedReport(
            oldReport, CoverageReportCreator.create(suite, models));
    writeContent(jsonFile, () -> CoverageReportUtil.toJson(aggregatedReport));
  }

  /**
   * Writes content to a file with proper directory creation and encoding.
   *
   * <p>Creates any necessary parent directories, then writes the supplied content to the specified
   * file using UTF-8 encoding.
   *
   * @param destFile The destination file to write to
   * @param contentProvider Supplier function that provides the content to write
   * @throws RuntimeException if writing to the file fails
   */
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
