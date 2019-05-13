/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobHeaders;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.IncidentIntent;
import org.agrona.DirectBuffer;

public final class JobFailedProcessor implements TypedRecordProcessor<JobRecord> {

  private static final DirectBuffer DEFAULT_ERROR_MESSAGE = wrapString("No more retries left.");
  private final IncidentRecord incidentEvent = new IncidentRecord();

  @Override
  public void processRecord(
      TypedRecord<JobRecord> event,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final JobRecord value = event.getValue();

    if (value.getRetries() <= 0) {
      final JobHeaders jobHeaders = value.getHeaders();

      final DirectBuffer jobErrorMessage = value.getErrorMessage();
      DirectBuffer incidentErrorMessage = DEFAULT_ERROR_MESSAGE;
      if (jobErrorMessage.capacity() > 0) {
        incidentErrorMessage = jobErrorMessage;
      }

      incidentEvent.reset();
      incidentEvent
          .setErrorType(ErrorType.JOB_NO_RETRIES)
          .setErrorMessage(incidentErrorMessage)
          .setBpmnProcessId(jobHeaders.getBpmnProcessId())
          .setWorkflowKey(jobHeaders.getWorkflowKey())
          .setWorkflowInstanceKey(jobHeaders.getWorkflowInstanceKey())
          .setElementId(jobHeaders.getElementId())
          .setElementInstanceKey(jobHeaders.getElementInstanceKey())
          .setJobKey(event.getKey())
          .setVariableScopeKey(jobHeaders.getElementInstanceKey());

      streamWriter.appendNewCommand(IncidentIntent.CREATE, incidentEvent);
    }
  }
}
