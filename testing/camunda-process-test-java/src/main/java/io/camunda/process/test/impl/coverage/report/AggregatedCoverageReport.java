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
import io.camunda.process.test.impl.coverage.model.Model;
import java.util.Collection;

public class AggregatedCoverageReport {
  private final Collection<AggregatedSuiteInfo> suites;
  private final Collection<Model> models;
  private final Collection<Coverage> coverages;

  public AggregatedCoverageReport(
      final Collection<AggregatedSuiteInfo> suites,
      final Collection<Model> models,
      final Collection<Coverage> coverages) {
    this.suites = suites;
    this.models = models;
    this.coverages = coverages;
  }

  public Collection<AggregatedSuiteInfo> getSuites() {
    return suites;
  }

  public Collection<Model> getModels() {
    return models;
  }

  public Collection<Coverage> getCoverages() {
    return coverages;
  }
}
