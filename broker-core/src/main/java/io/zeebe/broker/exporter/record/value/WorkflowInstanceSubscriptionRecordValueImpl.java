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
import io.zeebe.broker.exporter.record.RecordValueWithVariablesImpl;
import io.zeebe.exporter.api.record.value.WorkflowInstanceSubscriptionRecordValue;
import java.util.Objects;

public class WorkflowInstanceSubscriptionRecordValueImpl extends RecordValueWithVariablesImpl
    implements WorkflowInstanceSubscriptionRecordValue {
  private final String messageName;
  private final long workflowInstanceKey;
  private final long elementInstanceKey;

  public WorkflowInstanceSubscriptionRecordValueImpl(
      final ExporterObjectMapper objectMapper,
      final String variables,
      final String messageName,
      final long workflowInstanceKey,
      final long elementInstanceKey) {
    super(objectMapper, variables);
    this.messageName = messageName;
    this.workflowInstanceKey = workflowInstanceKey;
    this.elementInstanceKey = elementInstanceKey;
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
  public long getElementInstanceKey() {
    return elementInstanceKey;
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
        && elementInstanceKey == that.elementInstanceKey
        && Objects.equals(messageName, that.messageName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), messageName, workflowInstanceKey, elementInstanceKey);
  }

  @Override
  public String toString() {
    return "WorkflowInstanceSubscriptionRecordValueImpl{"
        + "messageName='"
        + messageName
        + '\''
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", variables='"
        + variables
        + '\''
        + '}';
  }
}
