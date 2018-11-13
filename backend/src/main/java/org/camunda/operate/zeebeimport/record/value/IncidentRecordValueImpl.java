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
package org.camunda.operate.zeebeimport.record.value;

import java.util.Objects;
import org.camunda.operate.zeebeimport.record.RecordValueWithPayloadImpl;
import io.zeebe.exporter.record.value.IncidentRecordValue;

public class IncidentRecordValueImpl extends RecordValueWithPayloadImpl
    implements IncidentRecordValue {
  private String errorType;
  private String errorMessage;
  private String bpmnProcessId;
  private String elementId;
  private long workflowInstanceKey;
  private long elementInstanceKey;
  private long jobKey;

  public IncidentRecordValueImpl() {
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

  public void setErrorType(String errorType) {
    this.errorType = errorType;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setElementId(String elementId) {
    this.elementId = elementId;
  }

  public void setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setJobKey(long jobKey) {
    this.jobKey = jobKey;
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
    final IncidentRecordValueImpl that = (IncidentRecordValueImpl) o;
    return workflowInstanceKey == that.workflowInstanceKey
        && elementInstanceKey == that.elementInstanceKey
        && jobKey == that.jobKey
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
        bpmnProcessId, elementId,
        workflowInstanceKey, elementInstanceKey,
        jobKey);
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
        + ", payload='"
        + getPayload()
        + '\''
        + '}';
  }
}
