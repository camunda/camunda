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

import io.camunda.client.api.statistics.response.ProcessFlowNodeStatistics;
import io.camunda.client.protocol.rest.ProcessFlowNodeStatisticsResult;
import java.util.Objects;

public class ProcessFlowNodeStatisticsImpl implements ProcessFlowNodeStatistics {

  private final String flowNodeId;
  private final Long active;
  private final Long canceled;
  private final Long incidents;
  private final Long completed;

  public ProcessFlowNodeStatisticsImpl(final ProcessFlowNodeStatisticsResult statistics) {
    flowNodeId = statistics.getFlowNodeId();
    active = statistics.getActive();
    canceled = statistics.getCanceled();
    incidents = statistics.getIncidents();
    completed = statistics.getCompleted();
  }

  public ProcessFlowNodeStatisticsImpl(
      final String flowNodeId,
      final Long active,
      final Long canceled,
      final Long incidents,
      final Long completed) {
    this.flowNodeId = flowNodeId;
    this.active = active;
    this.canceled = canceled;
    this.incidents = incidents;
    this.completed = completed;
  }

  @Override
  public String getFlowNodeId() {
    return flowNodeId;
  }

  @Override
  public Long getActive() {
    return active;
  }

  @Override
  public Long getCanceled() {
    return canceled;
  }

  @Override
  public Long getIncidents() {
    return incidents;
  }

  @Override
  public Long getCompleted() {
    return completed;
  }

  @Override
  public int hashCode() {
    return Objects.hash(flowNodeId, active, canceled, incidents, completed);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessFlowNodeStatisticsImpl that = (ProcessFlowNodeStatisticsImpl) o;
    return Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(active, that.active)
        && Objects.equals(canceled, that.canceled)
        && Objects.equals(incidents, that.incidents)
        && Objects.equals(completed, that.completed);
  }

  @Override
  public String toString() {
    return String.format(
        "ProcessDefinitionFlowNodeStatisticsImpl{flowNodeId='%s', active=%d, canceled=%d, incidents=%d, completed=%d}",
        flowNodeId, active, canceled, incidents, completed);
  }
}
