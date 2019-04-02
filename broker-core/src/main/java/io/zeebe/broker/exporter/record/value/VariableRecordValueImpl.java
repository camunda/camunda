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
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import java.util.Objects;

public class VariableRecordValueImpl extends RecordValueImpl implements VariableRecordValue {

  private final String name;
  private final String value;
  private final long scopeKey;
  private final long workflowInstanceKey;
  private final long workflowKey;

  public VariableRecordValueImpl(
      final ExporterObjectMapper objectMapper,
      String name,
      String value,
      long variableScopeKey,
      long workflowInstanceKey,
      long workflowKey) {
    super(objectMapper);
    this.name = name;
    this.value = value;
    this.scopeKey = variableScopeKey;
    this.workflowInstanceKey = workflowInstanceKey;
    this.workflowKey = workflowKey;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public long getScopeKey() {
    return scopeKey;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableRecordValueImpl that = (VariableRecordValueImpl) o;
    return scopeKey == that.scopeKey
        && workflowInstanceKey == that.workflowInstanceKey
        && workflowKey == that.workflowKey
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value, scopeKey, workflowInstanceKey, workflowKey);
  }

  @Override
  public String toString() {
    return "VariableRecordValueImpl{"
        + "name='"
        + name
        + '\''
        + ", value='"
        + value
        + '\''
        + ", scopeKey="
        + scopeKey
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", workflowKey="
        + workflowKey
        + '}';
  }
}
