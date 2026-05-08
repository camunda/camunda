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

import java.util.Objects;

public class DecisionModel {

  /** The decision definition id. */
  private final String decisionDefinitionId;

  /** Total count of rules in the decision table. */
  private final int totalRuleCount;

  /** Version of the decision definition. */
  private final String version;

  /** XML representation of the decision requirements (DRD). */
  private final String xml;

  public DecisionModel(
      final String decisionDefinitionId,
      final int totalRuleCount,
      final String version,
      final String xml) {
    this.decisionDefinitionId = decisionDefinitionId;
    this.totalRuleCount = totalRuleCount;
    this.version = version;
    this.xml = xml;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public int getTotalRuleCount() {
    return totalRuleCount;
  }

  public String getVersion() {
    return version;
  }

  public String xml() {
    return xml;
  }

  @Override
  public int hashCode() {
    return Objects.hash(decisionDefinitionId, totalRuleCount, version, xml);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final DecisionModel other = (DecisionModel) obj;
    return decisionDefinitionId.equals(other.decisionDefinitionId)
        && totalRuleCount == other.totalRuleCount
        && version.equals(other.version)
        && xml.equals(other.xml);
  }
}
