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
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import java.util.Objects;

public class IncidentRecordValueImpl extends RecordValueImpl implements IncidentRecordValue {
  private final String errorType;
  private final String errorMessage;
  private final String bpmnProcessId;
  private final String elementId;
  private final long workflowKey;
  private final long workflowInstanceKey;
  private final long elementInstanceKey;
  private final long jobKey;
  private final long variableScopeKey;

  public IncidentRecordValueImpl(
      final ExporterObjectMapper objectMapper,
      final String errorType,
      final String errorMessage,
      final String bpmnProcessId,
      final String elementId,
      final long workflowKey,
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final long jobKey,
      final long variableScopeKey) {
    super(objectMapper);
    this.errorType = errorType;
    this.errorMessage = errorMessage;
    this.bpmnProcessId = bpmnProcessId;
    this.elementId = elementId;
    this.workflowKey = workflowKey;
    this.workflowInstanceKey = workflowInstanceKey;
    this.elementInstanceKey = elementInstanceKey;
    this.jobKey = jobKey;
    this.variableScopeKey = variableScopeKey;
  }

  @Override
  public String getErrorType() {
    return errorType;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
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
  public long getWorkflowKey() {
    return workflowKey;
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
  public long getJobKey() {
    return jobKey;
  }

  @Override
  public long getVariableScopeKey() {
    return variableScopeKey;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final IncidentRecordValueImpl that = (IncidentRecordValueImpl) o;
    return workflowInstanceKey == that.workflowInstanceKey
        && elementInstanceKey == that.elementInstanceKey
        && jobKey == that.jobKey
        && variableScopeKey == that.variableScopeKey
        && Objects.equals(errorType, that.errorType)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        errorType,
        errorMessage,
        bpmnProcessId,
        elementId,
        workflowInstanceKey,
        elementInstanceKey,
        jobKey,
        variableScopeKey);
  }

  @Override
  public String toString() {
    return "IncidentRecordValueImpl{"
        + "errorType='"
        + errorType
        + '\''
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", elementId='"
        + elementId
        + '\''
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", jobKey="
        + jobKey
        + ", variableScopeKey="
        + variableScopeKey
        + '}';
  }
}
