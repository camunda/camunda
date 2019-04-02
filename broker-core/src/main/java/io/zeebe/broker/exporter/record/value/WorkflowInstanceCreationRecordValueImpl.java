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
import io.zeebe.exporter.api.record.value.WorkflowInstanceCreationRecordValue;
import java.util.Map;
import java.util.Objects;

public class WorkflowInstanceCreationRecordValueImpl extends RecordValueImpl
    implements WorkflowInstanceCreationRecordValue {
  private final String bpmnProcessId;
  private final int version;
  private final long key;
  private final long instanceKey;
  private final Map<String, Object> variables;

  public WorkflowInstanceCreationRecordValueImpl(
      ExporterObjectMapper objectMapper,
      String bpmnProcessId,
      int version,
      long key,
      long instanceKey,
      Map<String, Object> variables) {
    super(objectMapper);
    this.bpmnProcessId = bpmnProcessId;
    this.version = version;
    this.key = key;
    this.instanceKey = instanceKey;
    this.variables = variables;
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
  public long getKey() {
    return key;
  }

  @Override
  public long getInstanceKey() {
    return instanceKey;
  }

  @Override
  public Map<String, Object> getVariables() {
    return variables;
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
        && getKey() == that.getKey()
        && getInstanceKey() == that.getInstanceKey()
        && Objects.equals(getBpmnProcessId(), that.getBpmnProcessId())
        && Objects.equals(getVariables(), that.getVariables());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getBpmnProcessId(), getVersion(), getKey(), getInstanceKey(), getVariables());
  }

  @Override
  public String toString() {
    return "WorkflowInstanceCreationRecordValueImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", key="
        + key
        + ", workflowInstanceKey="
        + instanceKey
        + ", variables="
        + variables
        + "}";
  }
}
