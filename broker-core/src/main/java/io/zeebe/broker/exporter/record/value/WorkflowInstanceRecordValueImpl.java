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
import io.zeebe.broker.exporter.record.RecordValueWithPayloadImpl;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import java.util.Objects;

public class WorkflowInstanceRecordValueImpl extends RecordValueWithPayloadImpl
    implements WorkflowInstanceRecordValue {
  private final String bpmnProcessId;
  private final String elementId;
  private final int version;
  private final long workflowKey;
  private final long workflowInstanceKey;
  private final long scopeInstanceKey;

  public WorkflowInstanceRecordValueImpl(
      final ExporterObjectMapper objectMapper,
      final String payload,
      final String bpmnProcessId,
      final String elementId,
      final int version,
      final long workflowKey,
      final long workflowInstanceKey,
      final long scopeInstanceKey) {
    super(objectMapper, payload);
    this.bpmnProcessId = bpmnProcessId;
    this.elementId = elementId;
    this.version = version;
    this.workflowKey = workflowKey;
    this.workflowInstanceKey = workflowInstanceKey;
    this.scopeInstanceKey = scopeInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public long getScopeInstanceKey() {
    return scopeInstanceKey;
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
    final WorkflowInstanceRecordValueImpl that = (WorkflowInstanceRecordValueImpl) o;
    return version == that.version
        && workflowKey == that.workflowKey
        && workflowInstanceKey == that.workflowInstanceKey
        && scopeInstanceKey == that.scopeInstanceKey
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        bpmnProcessId,
        elementId,
        version,
        workflowKey,
        workflowInstanceKey,
        scopeInstanceKey);
  }

  @Override
  public String toString() {
    return "WorkflowInstanceRecordValueImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", elementId='"
        + elementId
        + '\''
        + ", version="
        + version
        + ", workflowKey="
        + workflowKey
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", scopeInstanceKey="
        + scopeInstanceKey
        + ", payload='"
        + payload
        + '\''
        + '}';
  }
}
