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

import io.zeebe.broker.exporter.record.RecordValueWithPayloadImpl;
import io.zeebe.exporter.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.gateway.impl.data.ZeebeObjectMapperImpl;
import java.util.Objects;

public class WorkflowInstanceSubscriptionRecordValueImpl extends RecordValueWithPayloadImpl
    implements WorkflowInstanceSubscriptionRecordValue {
  private final String messageName;
  private final long workflowInstanceKey;
  private final long activityInstanceKey;

  public WorkflowInstanceSubscriptionRecordValueImpl(
      final ZeebeObjectMapperImpl objectMapper,
      final String payload,
      final String messageName,
      final long workflowInstanceKey,
      final long activityInstanceKey) {
    super(objectMapper, payload);
    this.messageName = messageName;
    this.workflowInstanceKey = workflowInstanceKey;
    this.activityInstanceKey = activityInstanceKey;
  }

  @Override
  public String getMessageName() {
    return messageName;
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
    if (!super.equals(o)) {
      return false;
    }
    final WorkflowInstanceSubscriptionRecordValueImpl that =
        (WorkflowInstanceSubscriptionRecordValueImpl) o;
    return workflowInstanceKey == that.workflowInstanceKey
        && activityInstanceKey == that.activityInstanceKey
        && Objects.equals(messageName, that.messageName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), messageName, workflowInstanceKey, activityInstanceKey);
  }

  @Override
  public String toString() {
    return "WorkflowInstanceSubscriptionRecordValueImpl{"
        + "messageName='"
        + messageName
        + '\''
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", activityInstanceKey="
        + activityInstanceKey
        + ", payload='"
        + payload
        + '\''
        + '}';
  }
}
