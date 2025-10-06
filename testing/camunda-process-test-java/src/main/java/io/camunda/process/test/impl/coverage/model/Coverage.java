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

import java.util.List;

public class Coverage {
  private final String processDefinitionId;
  private final List<String> completedElements;
  private final List<String> takenSequenceFlows;
  private final double coverage;

  public Coverage(
      final String processDefinitionId,
      final List<String> completedElements,
      final List<String> takenSequenceFlows,
      final double coverage) {
    this.processDefinitionId = processDefinitionId;
    this.completedElements = completedElements;
    this.takenSequenceFlows = takenSequenceFlows;
    this.coverage = coverage;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public List<String> getCompletedElements() {
    return completedElements;
  }

  public List<String> getTakenSequenceFlows() {
    return takenSequenceFlows;
  }

  public double getCoverage() {
    return coverage;
  }
}
