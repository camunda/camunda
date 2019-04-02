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
import io.zeebe.exporter.api.record.value.ErrorRecordValue;
import java.util.Objects;

public class ErrorRecordValueImpl extends RecordValueImpl implements ErrorRecordValue {

  private final String exceptionMessage;
  private final String stacktrace;
  private final long errorEventPosition;

  private final long workflowInstanceKey;

  public ErrorRecordValueImpl(
      ExporterObjectMapper objectMapper,
      String exceptionMessage,
      String stacktrace,
      long errorEventPosition,
      long workflowInstanceKey) {
    super(objectMapper);
    this.exceptionMessage = exceptionMessage;
    this.stacktrace = stacktrace;
    this.errorEventPosition = errorEventPosition;
    this.workflowInstanceKey = workflowInstanceKey;
  }

  @Override
  public String getExceptionMessage() {
    return exceptionMessage;
  }

  @Override
  public String getStacktrace() {
    return stacktrace;
  }

  @Override
  public long getErrorEventPosition() {
    return errorEventPosition;
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
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ErrorRecordValueImpl that = (ErrorRecordValueImpl) o;
    return errorEventPosition == that.errorEventPosition
        && workflowInstanceKey == that.workflowInstanceKey
        && Objects.equals(exceptionMessage, that.exceptionMessage)
        && Objects.equals(stacktrace, that.stacktrace);
  }

  @Override
  public int hashCode() {
    return Objects.hash(exceptionMessage, stacktrace, errorEventPosition, workflowInstanceKey);
  }

  @Override
  public String toString() {
    return "ErrorRecordValueImpl{"
        + "exceptionMessage='"
        + exceptionMessage
        + '\''
        + ", stacktrace='"
        + stacktrace
        + '\''
        + ", errorEventPosition="
        + errorEventPosition
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + '}';
  }
}
