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
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * Coverage report for a single test run.
 *
 * <p>Represents the process and decision coverage produced by one execution of a test case.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableCoverageRunReport.Builder.class)
public interface CoverageRunReport {
  /** Returns the test method name. */
  String getName();

  /**
   * Returns the display name of the test case, or {@code null} if no custom display name is set.
   *
   * <p>In JUnit 5, this corresponds to the value of the {@code @DisplayName} annotation. When
   * present, the display name should be shown as the primary identifier in the report, with the
   * method name ({@link #getName()}) shown as the secondary identifier.
   */
  @Nullable
  String getDisplayName();

  /**
   * Returns the parameter representation for a parameterized test invocation, or {@code null} for
   * non-parameterized tests.
   *
   * <p>For parameterized tests, this contains the parameters used for a specific invocation (e.g.
   * {@code "[1] value1, value2"}). Multiple invocations of the same test method share the same
   * {@link #getName()} but have different parameter values.
   */
  @Nullable
  String getTestParameters();

  /** Returns process coverage entries calculated for this run. */
  List<ProcessCoverage> getProcessCoverages();

  /** Returns decision coverage entries calculated for this run. */
  List<DecisionCoverage> getDecisionCoverages();
}
