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
package io.zeebe.broker.incident.processor;

import io.zeebe.broker.job.JobState;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.workflow.processor.BpmnStepProcessor;
import io.zeebe.broker.workflow.processor.SideEffectQueue;
import io.zeebe.broker.workflow.state.IndexedRecord;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.IncidentIntent;
import java.util.function.Consumer;

public final class ResolveIncidentProcessor implements TypedRecordProcessor<IncidentRecord> {

  public static final String RESOLVE_REJECT_MESSAGE =
      "Expected to resolve an incident with key %d, but no incident found.";

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
    final String errorMessage = String.format(RESOLVE_REJECT_MESSAGE, incidentKey);

    streamWriter.appendRejection(command, RejectionType.NOT_APPLICABLE, errorMessage);
    responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_APPLICABLE, errorMessage);
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

  public void attemptToContinueWorkflowProcessing(
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
      queue.add(() -> responseWriter.flush());

      stepProcessor.processRecord(
          typedRecord, responseWriter, streamWriter, (producer) -> queue.add(producer));

      sideEffect.accept(queue);
    }
  }

  public void attemptToMakeJobActivatableAgain(long jobKey) {
    final JobState jobState = zeebeState.getJobState();
    final JobRecord job = jobState.getJob(jobKey);
    if (job != null) {
      jobState.resolve(jobKey, job);
    }
  }
}
