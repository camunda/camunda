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
package io.camunda.process.test.api.mock;

import io.camunda.process.test.impl.mock.VariablesTableImpl;
import java.util.List;
import java.util.Map;

/**
 * A table of {@code when}/{@code variables} rows used with {@link
 * JobWorkerMockBuilder#thenComplete(VariablesTable)} to derive a mock job worker's output variables
 * from the job's own variables.
 *
 * <p>Rows are evaluated in order. The first row whose {@code when} map is a <b>subset match</b> of
 * the job's variables supplies the output variables. Every entry in {@code when} must be present in
 * the job's variables with an equal value, after both sides are normalized through the client's
 * JSON mapper (so {@code 1} and {@code 1L} both match an integer job variable. But {@code 1.0} does
 * not match {@code 1} — there is no numeric widening). Nested maps and lists are compared as whole
 * values. A {@code null} value in {@code when} matches only a job variable that is present and
 * {@code null}; a missing key never matches. An empty {@code when} matches every job, so it can be
 * used as a catch-all when it is the last row. If no row matches, the job completes without
 * variables.
 *
 * <p>Example usage:
 *
 * <pre>
 *   processTestContext
 *       .mockJobWorker("myworker")
 *       .thenComplete(
 *           VariablesTable.when(Map.of("loopCounter", 1))
 *               .thenReturn(Map.of("sampleType", "regolith"))
 *               .when(Map.of("loopCounter", 2))
 *               .thenReturn(Map.of("sampleType", "basalt")));
 * </pre>
 */
public interface VariablesTable {

  /**
   * Starts building a new {@link VariablesTable} with a first row that matches the given {@code
   * when} map.
   *
   * @param match the variables to match against the job's variables.
   * @return a {@link Then} to configure the matching row's output variables.
   */
  static Then when(final Map<String, Object> match) {
    return VariablesTableImpl.EMPTY.when(match);
  }

  /**
   * Gets the rows of this table, in insertion order.
   *
   * @return an unmodifiable, ordered list of rows.
   */
  List<Row> getRows();

  /** Configures the output variables for the row currently being built. */
  interface Then {

    /**
     * Sets the output variables for the row currently being built.
     *
     * @param variables the variables to complete the job with when this row matches.
     * @return a {@link Builder} to add further rows or use as a {@link VariablesTable}.
     */
    Builder thenReturn(Map<String, Object> variables);
  }

  /** A {@link VariablesTable} under construction that can still accept further rows. */
  interface Builder extends VariablesTable {

    /**
     * Adds a further row that matches the given {@code when} map.
     *
     * @param match the variables to match against the job's variables.
     * @return a {@link Then} to configure the new row's output variables.
     */
    Then when(Map<String, Object> match);
  }

  /** A single row of a {@link VariablesTable}. */
  interface Row {

    /**
     * Gets the variables to match against the job's variables.
     *
     * @return an unmodifiable map, possibly empty (a catch-all) or containing {@code null} values.
     */
    Map<String, Object> getWhen();

    /**
     * Gets the variables to complete the job with when this row matches.
     *
     * @return an unmodifiable map.
     */
    Map<String, Object> getVariables();
  }
}
