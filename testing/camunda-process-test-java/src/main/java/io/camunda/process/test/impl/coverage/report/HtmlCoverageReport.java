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

import io.camunda.process.test.impl.coverage.model.Coverage;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class HtmlCoverageReport {
  private final Collection<SuiteCoverageReport> suites;
  private final Collection<Coverage> coverages;
  private final Map<String, String> definitions;

  public HtmlCoverageReport(
      final Collection<SuiteCoverageReport> suites,
      final Collection<Coverage> coverages,
      final Map<String, String> definitions) {
    this.suites = suites;
    this.coverages = coverages;
    this.definitions = definitions;
  }

  public Collection<SuiteCoverageReport> getSuites() {
    return Collections.unmodifiableCollection(suites);
  }

  public Collection<Coverage> getCoverages() {
    return Collections.unmodifiableCollection(coverages);
  }

  public Map<String, String> getDefinitions() {
    return Collections.unmodifiableMap(definitions);
  }
}
