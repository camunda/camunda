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
package io.zeebe.engine.processor.workflow.incident;

import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.BpmnStepProcessor;
import io.zeebe.engine.processor.workflow.SideEffectQueue;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.IncidentState;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.IncidentIntent;
import java.util.function.Consumer;

public final class ResolveIncidentProcessor implements TypedRecordProcessor<IncidentRecord> {

  public static final String NO_INCIDENT_FOUND_MSG =
      "Expected to resolve incident with key '%d', but no such incident was found";

  private final BpmnStepProcessor stepProcessor;
  private final ZeebeState zeebeState;
  private final TypedWorkflowInstanceRecord typedRecord = new TypedWorkflowInstanceRecord();
  private final SideEffectQueue queue = new SideEffectQueue();

  public ResolveIncidentProcessor(BpmnStepProcessor stepProcessor, ZeebeState zeebeState) {
    this.stepProcessor = stepProcessor;
    this.zeebeState = zeebeState;
  }

  @Override
  public void processRecord(
      TypedRecord<IncidentRecord> command,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {
    final long incidentKey = command.getKey();
    final IncidentState incidentState = zeebeState.getIncidentState();

    final IncidentRecord incidentRecord = incidentState.getIncidentRecord(incidentKey);
    if (incidentRecord != null) {
      incidentState.deleteIncident(incidentKey);

      streamWriter.appendFollowUpEvent(incidentKey, IncidentIntent.RESOLVED, incidentRecord);
      responseWriter.writeEventOnCommand(
          incidentKey, IncidentIntent.RESOLVED, incidentRecord, command);

      // workflow / job is already cleared if canceled, then we simply delete without resolving
      attemptToResolveIncident(responseWriter, streamWriter, sideEffect, incidentRecord);
    } else {
      rejectResolveCommand(command, responseWriter, streamWriter, incidentKey);
    }
  }

  private void rejectResolveCommand(
      TypedRecord<IncidentRecord> command,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      long incidentKey) {
    final String errorMessage = String.format(NO_INCIDENT_FOUND_MSG, incidentKey);

    streamWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
    responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
  }

  private void attemptToResolveIncident(
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      IncidentRecord incidentRecord) {
    final long jobKey = incidentRecord.getJobKey();
    final boolean isJobIncident = jobKey > 0;

    if (isJobIncident) {
      attemptToMakeJobActivatableAgain(jobKey);
    } else {
      attemptToContinueWorkflowProcessing(responseWriter, streamWriter, sideEffect, incidentRecord);
    }
  }

  private void attemptToContinueWorkflowProcessing(
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      IncidentRecord incidentRecord) {
    final long elementInstanceKey = incidentRecord.getElementInstanceKey();
    final IndexedRecord failedRecord =
        zeebeState.getWorkflowState().getElementInstanceState().getFailedRecord(elementInstanceKey);

    if (failedRecord != null) {
      typedRecord.wrap(failedRecord);

      queue.clear();
      queue.add(responseWriter::flush);
      stepProcessor.processRecord(typedRecord, responseWriter, streamWriter, queue::add);

      sideEffect.accept(queue);
    }
  }

  private void attemptToMakeJobActivatableAgain(long jobKey) {
    final JobState jobState = zeebeState.getJobState();
    final JobRecord job = jobState.getJob(jobKey);
    if (job != null) {
      jobState.resolve(jobKey, job);
    }
  }
}
