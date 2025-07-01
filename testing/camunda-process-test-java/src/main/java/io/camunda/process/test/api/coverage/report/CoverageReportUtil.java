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
import io.camunda.process.test.api.coverage.export.CoverageStateJsonExporter;
import io.camunda.process.test.api.coverage.model.Suite;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Utility for generating graphical class and method coverage reports.
 *
 * @author macoun, z0rbas
 */
public class CoverageReportUtil {

  /** Root directory for all coverage reports. */
  public static String TARGET_DIR_ROOT =
      System.getProperty(
          "camunda-process-test-coverage.target-dir-root", "target/process-test-coverage/");

  public static final String REPORT_RESOURCES = "static";
  private static final Logger logger =
      Logger.getLogger(CoverageReportUtil.class.getCanonicalName());
  private static final String REPORT_TEMPLATE = "html/bpmn.report-template.html";

  public static void createReport(
      final CoverageCollector coverageCollector, final String reportDirectory) {
    writeReport(
        createCoverageStateResult(coverageCollector),
        true,
        getReportDirectory(ofNullable(reportDirectory).orElse(TARGET_DIR_ROOT), coverageCollector),
        "report.html",
        CoverageReportUtil::generateHtml);
  }

  public static void createJsonReport(
      final CoverageCollector coverageCollector, final String reportDirectory) {
    writeReport(
        createCoverageStateResult(coverageCollector),
        false,
        getReportDirectory(ofNullable(reportDirectory).orElse(TARGET_DIR_ROOT), coverageCollector),
        "report.json",
        result -> result);
  }

  private static String createCoverageStateResult(final CoverageCollector coverageCollector) {
    final Suite suite = coverageCollector.getActiveSuite();
    return CoverageStateJsonExporter.createCoverageStateResult(
        Collections.singleton(suite),
        coverageCollector.getModels().stream()
            .filter(it -> !suite.getEvents(it.getKey()).isEmpty())
            .collect(Collectors.toSet()));
  }

  public static void writeReport(
      final String coverageResult,
      final boolean installReportDependencies,
      final File reportDirectory,
      final String fileName,
      final Function<String, String> reportCreator) {

    if (installReportDependencies) {
      installReportDependencies(reportDirectory);
    }

    try {
      Files.createDirectories(reportDirectory.toPath());
      writeToFile(reportDirectory + "/" + fileName, reportCreator.apply(coverageResult));
    } catch (final IOException ex) {
      throw new RuntimeException("Unable to write report.", ex);
    }
  }

  public static String generateHtml(final String result) {
    final InputStream template =
        CoverageReportUtil.class.getClassLoader().getResourceAsStream(REPORT_TEMPLATE);
    Objects.requireNonNull(template);
    final String html =
        new BufferedReader(new InputStreamReader(template, StandardCharsets.UTF_8))
            .lines()
            .collect(Collectors.joining("\n"));
    return html.replace("{{__REPORT_JSON_PLACEHOLDER__}}", result);
  }

  private static void writeToFile(final String filePath, final String json) throws IOException {
    Files.write(FileSystems.getDefault().getPath(filePath), json.getBytes(StandardCharsets.UTF_8));
  }

  private static void installReportDependencies(final File reportDirectory) {
    final File parent = reportDirectory.getParentFile();
    final File reportResourcesDir = new File(parent, REPORT_RESOURCES);
    if (reportResourcesDir.exists()) {
      // No need to install
      return;
    }

    try {

      final File resourcesRoot = ClassLocationURL.fileFor(CoverageReportUtil.class);

      // Tests executed by maven use JAR resources
      if (resourcesRoot.isFile()) {

        final JarFile coverageJar = new JarFile(resourcesRoot);
        final Enumeration<JarEntry> entries = coverageJar.entries();

        while (entries.hasMoreElements()) {
          final String resourcePath = entries.nextElement().getName();
          if (resourcePath.startsWith(REPORT_RESOURCES)) {
            final File resource = new File(parent, resourcePath);
            final InputStream source =
                CoverageReportUtil.class.getResourceAsStream("/" + resourcePath);
            Objects.requireNonNull(source);
            if (resourcePath.endsWith("/")) {
              logger.info("Creating directory " + resource.getAbsolutePath());
              if (!resource.exists() && !resource.mkdirs()) {
                throw new IllegalStateException(
                    "Could not create report directory " + resource.getAbsolutePath());
              }
            } else {
              if (!resource.getParentFile().exists() && !resource.getParentFile().mkdirs()) {
                throw new IllegalStateException(
                    "Could not create report directory "
                        + resource.getParentFile().getAbsolutePath());
              }
              Files.copy(source, resource.toPath());
            }
          }
        }
        coverageJar.close();
      } else {
        // Tests executed in the IDE use directories
        final URL reportResources = CoverageReportUtil.class.getResource("/" + REPORT_RESOURCES);
        Objects.requireNonNull(reportResources);
        final File reportResourcesSrc = new File(reportResources.toURI());
        if (!reportResourcesDir.getParentFile().exists()
            && !reportResourcesDir.getParentFile().mkdirs()) {
          throw new IllegalStateException(
              "Could not create report directory "
                  + reportResourcesDir.getParentFile().getAbsolutePath());
        }
        copyFolder(reportResourcesSrc.toPath(), reportResourcesDir.toPath());
      }

    } catch (final Exception e) {
      throw new RuntimeException("Unable to copy report resources", e);
    }
  }

  private static void copyFolder(final Path source, final Path target) throws IOException {
    Files.walkFileTree(
        source,
        new SimpleFileVisitor<Path>() {

          @Override
          public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
              throws IOException {
            Files.createDirectories(target.resolve(source.relativize(dir)));
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
              throws IOException {
            Files.copy(file, target.resolve(source.relativize(file)));
            return FileVisitResult.CONTINUE;
          }
        });
  }

  /**
   * Retrieves directory path for all coverage reports of a test class.
   *
   * @param directory directory for the reports
   * @param collector collector that was used for collecting events
   * @return path for the report.
   */
  private static File getReportDirectory(
      final String directory, final CoverageCollector collector) {
    return new File(directory, collector.getActiveSuite().getId());
  }
}
