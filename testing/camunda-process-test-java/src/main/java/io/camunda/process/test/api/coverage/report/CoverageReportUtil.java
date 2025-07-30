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
import java.nio.file.*;

/**
 * Utility class for JSON serialization and deserialization of coverage reports.
 *
 * <p>This class provides methods to convert CoverageReport objects to and from JSON format. It's
 * used in the process coverage reporting workflow to store coverage data.
 */
public class CoverageReportUtil {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
}
