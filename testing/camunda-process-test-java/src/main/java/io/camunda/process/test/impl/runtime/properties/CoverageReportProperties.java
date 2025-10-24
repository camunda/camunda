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
package io.camunda.process.test.impl.runtime.properties;

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyListOrEmpty;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;

import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import java.util.List;
import java.util.Properties;

public class CoverageReportProperties {

  public static final String PROPERTY_NAME_COVERAGE_REPORT_DIRECTORY = "coverage.reportDirectory";
  public static final String PROPERTY_NAME_COVERAGE_REPORT_EXCLUDED_PROCESSES =
      "coverage.excludedProcesses";

  private final String coverageReportDirectory;
  private final List<String> coverageExcludedProcesses;

  public CoverageReportProperties(final Properties properties) {
    coverageReportDirectory =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_COVERAGE_REPORT_DIRECTORY,
            CamundaProcessTestRuntimeDefaults.DEFAULT_COVERAGE_REPORT_DIRECTORY);

    coverageExcludedProcesses =
        getPropertyListOrEmpty(properties, PROPERTY_NAME_COVERAGE_REPORT_EXCLUDED_PROCESSES);
  }

  public String getCoverageReportDirectory() {
    return coverageReportDirectory;
  }

  public List<String> getCoverageExcludedProcesses() {
    return coverageExcludedProcesses;
  }
}
