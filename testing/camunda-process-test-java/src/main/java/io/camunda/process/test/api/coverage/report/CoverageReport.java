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

import io.camunda.process.test.api.coverage.model.Coverage;
import io.camunda.process.test.api.coverage.model.Model;
import io.camunda.process.test.api.coverage.model.Suite;
import java.util.Collection;
import java.util.Collections;

/** Container class for process coverage reporting data. */
public class CoverageReport {

  private final Collection<Suite> suites;
  private final Collection<Model> models;
  private final Collection<Coverage> coverages;

  /**
   * Creates a coverage report with the specified data collections.
   *
   * @param suites Collection of test suites containing coverage information
   * @param models Collection of process models with structure information
   * @param coverages Collection of coverage results for process instances
   */
  public CoverageReport(
      final Collection<Suite> suites,
      final Collection<Model> models,
      final Collection<Coverage> coverages) {
    this.suites = suites;
    this.models = models;
    this.coverages = coverages;
  }

  /**
   * Creates an empty coverage report.
   *
   * <p>Initializes all collections as empty. Useful as a starting point for building reports by
   * aggregating data later.
   */
  public CoverageReport() {
    this(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }

  public Collection<Suite> getSuites() {
    return suites;
  }

  public Collection<Model> getModels() {
    return models;
  }

  public Collection<Coverage> getCoverages() {
    return coverages;
  }
}
