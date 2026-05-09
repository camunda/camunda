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
package io.camunda.process.test.impl.coverage.model;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Run {

  /** The name of the run */
  private final String name;

  private final List<Coverage> coverages;
  private final List<DecisionCoverage> decisionCoverages;

  public Run(final String name, final List<Coverage> coverages) {
    this(name, coverages, Collections.emptyList());
  }

  public Run(
      final String name,
      final List<Coverage> coverages,
      final List<DecisionCoverage> decisionCoverages) {
    this.name = name;
    this.coverages = coverages;
    this.decisionCoverages = decisionCoverages;
  }

  public Collection<Coverage> getCoverages() {
    return coverages;
  }

  public Collection<DecisionCoverage> getDecisionCoverages() {
    return decisionCoverages;
  }

  public String getName() {
    return name;
  }
}
