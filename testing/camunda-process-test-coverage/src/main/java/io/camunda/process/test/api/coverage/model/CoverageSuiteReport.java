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
package io.camunda.process.test.api.coverage.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import org.immutables.value.Value;

/**
 * Coverage report for one test suite.
 *
 * <p>Contains per-run coverage breakdown and suite-level aggregated process and decision coverage.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableCoverageSuiteReport.Builder.class)
public interface CoverageSuiteReport {
  /** Returns the stable suite identifier. */
  String getId();

  /** Returns the suite display name. */
  String getName();

  /** Returns the coverage reports captured for each run in the suite. */
  List<CoverageRunReport> getRuns();

  /** Returns process coverage aggregated for this suite. */
  List<ProcessCoverage> getProcessCoverages();

  /** Returns decision coverage aggregated for this suite. */
  List<DecisionCoverage> getDecisionCoverages();
}
