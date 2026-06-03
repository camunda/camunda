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

import java.util.List;
import org.immutables.value.Value;

/**
 * Coverage report for a single test run.
 *
 * <p>Represents the process and decision coverage produced by one execution of a test case.
 */
@Value.Immutable
public interface CoverageRunReport {
  /** Returns the test run name. */
  String getName();

  /** Returns process coverage entries calculated for this run. */
  List<ProcessCoverage> getProcessCoverages();

  /** Returns decision coverage entries calculated for this run. */
  List<DecisionCoverage> getDecisionCoverages();
}
