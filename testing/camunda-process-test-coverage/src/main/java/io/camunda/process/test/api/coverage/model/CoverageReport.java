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
 * Top-level coverage report containing suite, model, and aggregated coverage data.
 *
 * <p>This payload is serialized for the coverage frontend and contains both summarized coverage
 * metrics and referenced model definitions used by the viewer.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableCoverageReport.Builder.class)
public interface CoverageReport {

  /** Returns all suite-level coverage reports included in this report. */
  List<CoverageSuiteReport> getSuites();

  /** Returns all process models referenced by the included process coverage entries. */
  List<ProcessModel> getProcessModels();

  /** Returns all decision models referenced by the included decision coverage entries. */
  List<DecisionModel> getDecisionModels();

  /** Returns process coverage aggregated across all captured suites and runs. */
  List<ProcessCoverage> getProcessCoverages();

  /** Returns decision coverage aggregated across all captured suites and runs. */
  List<DecisionCoverage> getDecisionCoverages();

}
