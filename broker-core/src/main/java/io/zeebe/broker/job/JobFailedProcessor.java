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
package io.zeebe.broker.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
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
          .setWorkflowInstanceKey(jobHeaders.getWorkflowInstanceKey())
          .setElementId(jobHeaders.getElementId())
          .setElementInstanceKey(jobHeaders.getElementInstanceKey())
          .setJobKey(event.getKey());

      streamWriter.appendNewCommand(IncidentIntent.CREATE, incidentEvent);
    }
  }
}
