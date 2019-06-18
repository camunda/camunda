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
import io.zeebe.protocol.record.value.WorkflowInstanceCreationRecordValue;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class WorkflowInstanceCreationRecordValueImpl extends RecordValueWithVariablesImpl
    implements WorkflowInstanceCreationRecordValue {
  private final String bpmnProcessId;
  private final int version;
  private final long workflowKey;
  private final long workflowInstanceKey;

  public WorkflowInstanceCreationRecordValueImpl(
      ExporterObjectMapper objectMapper,
      String bpmnProcessId,
      int version,
      long workflowKey,
      long workflowInstanceKey,
      Supplier<String> variablesSupplier,
      Supplier<Map<String, Object>> variableMapSupplier) {
    super(objectMapper, variablesSupplier, variableMapSupplier);
    this.bpmnProcessId = bpmnProcessId;
    this.version = version;
    this.workflowKey = workflowKey;
    this.workflowInstanceKey = workflowInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof WorkflowInstanceCreationRecordValueImpl)) {
      return false;
    }

    final WorkflowInstanceCreationRecordValueImpl that =
        (WorkflowInstanceCreationRecordValueImpl) o;
    return getVersion() == that.getVersion()
        && getWorkflowKey() == that.getWorkflowKey()
        && getWorkflowInstanceKey() == that.getWorkflowInstanceKey()
        && Objects.equals(getBpmnProcessId(), that.getBpmnProcessId())
        && Objects.equals(getVariablesAsMap(), that.getVariablesAsMap());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getBpmnProcessId(),
        getVersion(),
        getWorkflowKey(),
        getWorkflowInstanceKey(),
        getVariablesAsMap());
  }

  @Override
  public String toString() {
    return "WorkflowInstanceCreationRecordValueImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", workflowKey="
        + workflowKey
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", variables="
        + getVariablesAsMap()
        + "}";
  }
}
