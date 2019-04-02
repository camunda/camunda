/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.exporter.record.value;

import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.broker.exporter.record.RecordValueImpl;
import io.zeebe.exporter.api.record.value.TimerRecordValue;
import java.util.Objects;

public class TimerRecordValueImpl extends RecordValueImpl implements TimerRecordValue {

  private final long elementInstanceKey;
  private final long workflowInstanceKey;
  private final long dueDate;
  private final String handlerFlowNodeId;
  private final int repetitions;
  private final long workflowKey;

  public TimerRecordValueImpl(
      ExporterObjectMapper objectMapper,
      long elementInstanceKey,
      long workflowInstanceKey,
      long dueDate,
      String handlerFlowNodeId,
      int repetitions,
      long workflowKey) {
    super(objectMapper);
    this.elementInstanceKey = elementInstanceKey;
    this.dueDate = dueDate;
    this.handlerFlowNodeId = handlerFlowNodeId;
    this.workflowInstanceKey = workflowInstanceKey;

    this.repetitions = repetitions;
    this.workflowKey = workflowKey;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getDueDate() {
    return dueDate;
  }

  @Override
  public String getHandlerFlowNodeId() {
    return handlerFlowNodeId;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public int getRepetitions() {
    return repetitions;
  }

  @Override
  public int hashCode() {

    return Objects.hash(
        elementInstanceKey,
        workflowInstanceKey,
        dueDate,
        handlerFlowNodeId,
        workflowKey,
        repetitions);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final TimerRecordValueImpl other = (TimerRecordValueImpl) o;

    return elementInstanceKey == other.elementInstanceKey
        && workflowInstanceKey == other.workflowInstanceKey
        && dueDate == other.dueDate
        && repetitions == other.repetitions
        && workflowKey == other.workflowKey
        && Objects.equals(handlerFlowNodeId, other.handlerFlowNodeId);
  }

  @Override
  public String toString() {
    return "TimerRecordValueImpl{"
        + "elementInstanceKey="
        + elementInstanceKey
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", workflowKey="
        + workflowKey
        + ", dueDate="
        + dueDate
        + ", repetitions="
        + repetitions
        + ", handlerFlowNodeId='"
        + handlerFlowNodeId
        + '\''
        + '}';
  }
}
