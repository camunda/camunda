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
import io.zeebe.exporter.record.value.MessageSubscriptionRecordValue;
import java.util.Objects;

public class MessageSubscriptionRecordValueImpl extends RecordValueImpl
    implements MessageSubscriptionRecordValue {
  private final String messageName;
  private final String correlationKey;
  private final int workflowInstancePartitionId;
  private final long workflowInstanceKey;
  private final long activityInstanceKey;

  public MessageSubscriptionRecordValueImpl(
      final ExporterObjectMapper objectMapper,
      final String messageName,
      final String correlationKey,
      final int workflowInstancePartitionId,
      final long workflowInstanceKey,
      final long activityInstanceKey) {
    super(objectMapper);
    this.messageName = messageName;
    this.correlationKey = correlationKey;
    this.workflowInstancePartitionId = workflowInstancePartitionId;
    this.workflowInstanceKey = workflowInstanceKey;
    this.activityInstanceKey = activityInstanceKey;
  }

  @Override
  public String getMessageName() {
    return messageName;
  }

  @Override
  public String getCorrelationKey() {
    return correlationKey;
  }

  @Override
  public int getWorkflowInstancePartitionId() {
    return workflowInstancePartitionId;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public long getActivityInstanceKey() {
    return activityInstanceKey;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MessageSubscriptionRecordValueImpl that = (MessageSubscriptionRecordValueImpl) o;
    return workflowInstancePartitionId == that.workflowInstancePartitionId
        && workflowInstanceKey == that.workflowInstanceKey
        && activityInstanceKey == that.activityInstanceKey
        && Objects.equals(messageName, that.messageName)
        && Objects.equals(correlationKey, that.correlationKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        messageName,
        correlationKey,
        workflowInstancePartitionId,
        workflowInstanceKey,
        activityInstanceKey);
  }

  @Override
  public String toString() {
    return "MessageSubscriptionRecordValueImpl{"
        + "messageName='"
        + messageName
        + '\''
        + ", correlationKey='"
        + correlationKey
        + '\''
        + ", workflowInstancePartitionId="
        + workflowInstancePartitionId
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", activityInstanceKey="
        + activityInstanceKey
        + '}';
  }
}
