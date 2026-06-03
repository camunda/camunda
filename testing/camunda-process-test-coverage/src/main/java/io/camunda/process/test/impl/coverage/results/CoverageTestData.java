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
package io.camunda.process.test.impl.coverage.results;

import java.util.List;
import org.immutables.value.Value;

/**
 * Aggregate coverage input collected during a test run.
 *
 * <p>Groups process and decision instance data together with their corresponding deployed
 * definitions so coverage can be calculated and reported.
 */
@Value.Immutable
public interface CoverageTestData {

  /** Returns process instance execution data captured during the test run. */
  List<CoverageProcessInstanceData> getProcessInstanceData();

  /** Returns decision evaluation data captured during the test run. */
  List<CoverageDecisionInstanceData> getDecisionInstanceData();

  /** Returns process definitions referenced by collected process instance data. */
  List<CoverageProcessDefinitionData> getProcessDefinitionData();

  /** Returns decision definitions referenced by collected decision instance data. */
  List<CoverageDecisionDefinitionData> getDecisionDefinitionData();
}
