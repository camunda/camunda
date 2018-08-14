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
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.gateway.impl.data.ZeebeObjectMapperImpl;
import java.util.Objects;

public class IncidentRecordValueImpl extends RecordValueWithPayloadImpl
    implements IncidentRecordValue {
  private final String errorType;
  private final String errorMessage;
  private final String bpmnProcessId;
  private final String activityId;
  private final long workflowInstanceKey;
  private final long activityInstanceKey;
  private final long jobKey;

  public IncidentRecordValueImpl(
      final ZeebeObjectMapperImpl objectMapper,
      final String payload,
      final String errorType,
      final String errorMessage,
      final String bpmnProcessId,
      final String activityId,
      final long workflowInstanceKey,
      final long activityInstanceKey,
      final long jobKey) {
    super(objectMapper, payload);
    this.errorType = errorType;
    this.errorMessage = errorMessage;
    this.bpmnProcessId = bpmnProcessId;
    this.activityId = activityId;
    this.workflowInstanceKey = workflowInstanceKey;
    this.activityInstanceKey = activityInstanceKey;
    this.jobKey = jobKey;
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
  public String getActivityId() {
    return activityId;
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
  public long getJobKey() {
    return jobKey;
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
        && activityInstanceKey == that.activityInstanceKey
        && jobKey == that.jobKey
        && Objects.equals(errorType, that.errorType)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(activityId, that.activityId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        errorType,
        errorMessage,
        bpmnProcessId,
        activityId,
        workflowInstanceKey,
        activityInstanceKey,
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
        + ", activityId='"
        + activityId
        + '\''
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", activityInstanceKey="
        + activityInstanceKey
        + ", jobKey="
        + jobKey
        + ", payload='"
        + payload
        + '\''
        + '}';
  }
}
