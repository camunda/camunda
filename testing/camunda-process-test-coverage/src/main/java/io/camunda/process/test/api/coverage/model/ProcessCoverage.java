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
 * Coverage details for one process definition.
 *
 * <p>Tracks visited BPMN elements, traversed sequence flows, and the resulting coverage ratio.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableProcessCoverage.Builder.class)
public interface ProcessCoverage {
  /** Returns the covered process definition id. */
  String getProcessDefinitionId();

  /** Returns BPMN element ids that were completed at least once. */
  List<String> getCompletedElements();

  /** Returns sequence flow ids that were taken at least once. */
  List<String> getTakenSequenceFlows();

  /** Returns the normalized coverage ratio for this process definition. */
  double getCoverage();
}
