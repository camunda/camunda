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
package io.camunda.client.impl.statistics.response;

import io.camunda.client.api.statistics.response.UsageMetricsStatisticsItem;
import java.util.Objects;

public class UsageMetricsStatisticsItemImpl implements UsageMetricsStatisticsItem {

  protected final long processInstances;
  protected final long decisionInstances;
  protected final long assignees;

  public UsageMetricsStatisticsItemImpl(
      final long processInstances, final long decisionInstances, final long assignees) {
    this.processInstances = processInstances;
    this.decisionInstances = decisionInstances;
    this.assignees = assignees;
  }

  @Override
  public long getProcessInstances() {
    return processInstances;
  }

  @Override
  public long getDecisionInstances() {
    return decisionInstances;
  }

  @Override
  public long getAssignees() {
    return assignees;
  }

  @Override
  public int hashCode() {
    return Objects.hash(processInstances, decisionInstances, assignees);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UsageMetricsStatisticsItemImpl that = (UsageMetricsStatisticsItemImpl) o;
    return Objects.equals(processInstances, that.processInstances)
        && Objects.equals(decisionInstances, that.decisionInstances)
        && Objects.equals(assignees, that.assignees);
  }

  @Override
  public String toString() {
    return "UsageMetricsStatisticsItemImpl{"
        + "processInstances="
        + processInstances
        + ", decisionInstances="
        + decisionInstances
        + ", assignees="
        + assignees
        + '}';
  }
}
