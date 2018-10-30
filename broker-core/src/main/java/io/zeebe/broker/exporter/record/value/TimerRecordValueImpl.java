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
import io.zeebe.exporter.record.value.TimerRecordValue;
import java.util.Objects;

public class TimerRecordValueImpl extends RecordValueImpl implements TimerRecordValue {

  private final long elementInstanceKey;
  private final long dueDate;
  private final String handlerFlowNodeId;

  public TimerRecordValueImpl(
      ExporterObjectMapper objectMapper,
      long elementInstanceKey,
      long dueDate,
      String handlerFlowNodeId) {
    super(objectMapper);
    this.elementInstanceKey = elementInstanceKey;
    this.dueDate = dueDate;
    this.handlerFlowNodeId = handlerFlowNodeId;
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
  public int hashCode() {
    return Objects.hash(dueDate, elementInstanceKey, handlerFlowNodeId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final TimerRecordValueImpl other = (TimerRecordValueImpl) obj;
    if (elementInstanceKey != other.elementInstanceKey) {
      return false;
    }

    if (dueDate != other.dueDate) {
      return false;
    }

    return handlerFlowNodeId.equals(other.handlerFlowNodeId);
  }

  @Override
  public String toString() {
    return "TimerRecordValueImpl [elementInstanceKey="
        + elementInstanceKey
        + ", dueDate="
        + dueDate
        + ", handlerFlowNodeId"
        + handlerFlowNodeId
        + "]";
  }
}
