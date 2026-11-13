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
package io.camunda.process.test.api.testCases;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Map;
import org.immutables.value.Value;

/**
 * A row in a variables table used to derive job worker output variables from the job's own
 * variables. Rows are evaluated in order; the first row whose {@link #getWhen()} map is a subset
 * match of the job's variables supplies {@link #getVariables()} as the job's output.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableVariablesTableRow.Builder.class)
public interface VariablesTableRow {

  /**
   * The variables to match against the job's variables. Every entry must be present in the job's
   * variables with an equal value (a null value matches a present null) for this row to match. An
   * empty map matches every job.
   *
   * @return the variables to match
   */
  @AllowNulls
  Map<String, Object> getWhen();

  /**
   * The variables to complete the job with when this row matches.
   *
   * @return the output variables
   */
  @AllowNulls
  Map<String, Object> getVariables();
}
