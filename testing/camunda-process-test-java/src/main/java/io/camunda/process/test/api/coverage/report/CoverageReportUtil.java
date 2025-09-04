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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;

/**
 * Utility class for JSON serialization and deserialization of coverage reports.
 *
 * <p>This class provides methods to convert CoverageReport objects to and from JSON format. It's
 * used in the process coverage reporting workflow to store coverage data.
 */
public class CoverageReportUtil {

  public static final String REPORT_RESOURCES = "coverage/static";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String REPORT_TEMPLATE = "coverage/index.html";

  /**
   * Serializes a CoverageReport object to a JSON string.
   *
   * @param report The report object to serialize
   * @return JSON string representation of the coverage report
   * @throws RuntimeException if serialization fails
   */
  public static String toJson(final Object report) {
    try {
      return OBJECT_MAPPER.writer().withDefaultPrettyPrinter().writeValueAsString(report);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize object to Json : " + e);
    }
  }

  public static String toHtml(final HtmlCoverageReport coverageReport) {
    final InputStream template =
        CoverageReportUtil.class.getClassLoader().getResourceAsStream(REPORT_TEMPLATE);
    Objects.requireNonNull(template);
    final String html =
        new BufferedReader(new InputStreamReader(template, StandardCharsets.UTF_8))
            .lines()
            .collect(Collectors.joining("\n"));
    return html.replace("{{ COVERAGE_DATA }}", toJson(coverageReport));
  }

  static void installReportDependencies(final File reportDirectory) {
    final File reportResourcesDir = new File(reportDirectory, REPORT_RESOURCES);

    // Skip if resources are already installed
    if (reportResourcesDir.exists()) {
      return;
    }

    try {
      // Create parent directory if needed
      if (!reportDirectory.exists() && !reportDirectory.mkdirs()) {
        throw new IllegalStateException(
            "Could not create report parent directory: " + reportDirectory.getAbsolutePath());
      }

      // Get resource URL based on current environment (JAR or IDE)
      final URL resourceUrl = CoverageReportUtil.class.getResource("/" + REPORT_RESOURCES);
      if (resourceUrl == null) {
        throw new IllegalStateException("Report resources not found in classpath");
      }

      // Copy the resources
      final File source = new File(resourceUrl.toURI());
      if (source.isDirectory()) {
        FileUtils.copyDirectoryToDirectory(source, reportDirectory);
      } else {
        FileUtils.copyFileToDirectory(source, reportDirectory);
      }
    } catch (final Exception e) {
      throw new RuntimeException("Unable to copy report resources", e);
    }
  }
}
