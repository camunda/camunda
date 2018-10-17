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

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.incident.index.IncidentMap;
import io.zeebe.broker.logstreams.processor.CommandProcessor;
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.map.Long2LongZbMap;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.job.JobHeaders;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/** Is responsible for the incident lifecycle. */
public class IncidentStreamProcessor {
  private static final short STATE_CREATED = 1;
  private static final short STATE_RESOLVING = 2;
  private static final short STATE_DELETING = 3;

  private static final long NON_PERSISTENT_INCIDENT = -2L;

  private final Long2LongZbMap activityInstanceMap = new Long2LongZbMap();
  private final Long2LongZbMap failedJobMap = new Long2LongZbMap();
  private final IncidentMap incidentMap = new IncidentMap();
  private final Long2LongZbMap resolvingEvents = new Long2LongZbMap();

  public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment env) {
    TypedEventStreamProcessorBuilder builder =
        env.newStreamProcessor()
            .keyGenerator(KeyGenerator.createIncidentKeyGenerator(env.getStream().getPartitionId()))
            .withStateResource(activityInstanceMap)
            .withStateResource(failedJobMap)
            .withStateResource(incidentMap.getMap())
            .withStateResource(resolvingEvents);

    // incident events
    builder =
        builder
            .onCommand(ValueType.INCIDENT, IncidentIntent.CREATE, new CreateIncidentProcessor())
            .onCommand(ValueType.INCIDENT, IncidentIntent.RESOLVE, new ResolveIncidentProcessor())
            .onEvent(
                ValueType.INCIDENT, IncidentIntent.RESOLVE_FAILED, new ResolveFailedProcessor())
            .onCommand(ValueType.INCIDENT, IncidentIntent.DELETE, new DeleteIncidentProcessor());

    // workflow instance events
    final ActivityRewrittenProcessor activityRewrittenProcessor = new ActivityRewrittenProcessor();
    final ActivityIncidentResolvedProcessor activityIncidentResolvedProcessor =
        new ActivityIncidentResolvedProcessor();

    builder =
        builder
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.PAYLOAD_UPDATED,
                new PayloadUpdatedProcessor())
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_TERMINATED,
                new ActivityTerminatedProcessor())
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_READY,
                activityRewrittenProcessor)
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.GATEWAY_ACTIVATED,
                activityRewrittenProcessor)
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_COMPLETING,
                activityRewrittenProcessor)
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_ACTIVATED,
                activityIncidentResolvedProcessor)
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
                activityIncidentResolvedProcessor)
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ELEMENT_COMPLETED,
                activityIncidentResolvedProcessor);

    // job events
    final JobIncidentResolvedProcessor jobIncidentResolvedProcessor =
        new JobIncidentResolvedProcessor();

    builder =
        builder
            .onEvent(ValueType.JOB, JobIntent.FAILED, new JobFailedProcessor())
            .onEvent(ValueType.JOB, JobIntent.RETRIES_UPDATED, jobIncidentResolvedProcessor)
            .onEvent(ValueType.JOB, JobIntent.CANCELED, jobIncidentResolvedProcessor);

    return builder.build();
  }

  private final class CreateIncidentProcessor implements CommandProcessor<IncidentRecord> {

    @Override
    public void onCommand(
        TypedRecord<IncidentRecord> command, CommandControl<IncidentRecord> commandControl) {
      final IncidentRecord incidentEvent = command.getValue();

      final boolean isJobIncident = incidentEvent.getJobKey() > 0;

      if (isJobIncident
          && failedJobMap.get(incidentEvent.getJobKey(), -1L) != NON_PERSISTENT_INCIDENT) {
        commandControl.reject(RejectionType.NOT_APPLICABLE, "Job is not failed");
        return;
      }

      final long incidentKey = commandControl.accept(IncidentIntent.CREATED, incidentEvent);

      if (isJobIncident) {
        failedJobMap.put(incidentEvent.getJobKey(), incidentKey);
      } else {
        activityInstanceMap.put(incidentEvent.getElementInstanceKey(), incidentKey);
      }

      incidentMap
          .newIncident(incidentKey)
          .setState(STATE_CREATED)
          .setIncidentEventPosition(command.getPosition())
          .setFailureEventPosition(incidentEvent.getFailureEventPosition())
          .write();
    }
  }

  private final class PayloadUpdatedProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {

    private final IncidentRecord incidentEvent = new IncidentRecord();

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      final long incidentKey = activityInstanceMap.get(event.getKey(), -1L);

      if (incidentKey > 0 && incidentMap.wrapIncidentKey(incidentKey).getState() == STATE_CREATED) {
        final WorkflowInstanceRecord workflowInstanceEvent = event.getValue();

        incidentEvent.reset();
        incidentEvent
            .setWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
            .setElementInstanceKey(event.getKey())
            .setPayload(workflowInstanceEvent.getPayload());

        streamWriter.writeFollowUpCommand(incidentKey, IncidentIntent.RESOLVE, incidentEvent);
      }
    }
  }

  private final class ResolveIncidentProcessor implements TypedRecordProcessor<IncidentRecord> {
    private TypedStreamReader reader;

    private TypedRecord<WorkflowInstanceRecord> failureEvent;
    private long incidentKey;

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor) {
      reader = streamProcessor.getEnvironment().getStreamReader();
    }

    @Override
    public void processRecord(
        TypedRecord<IncidentRecord> command,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      incidentKey = command.getKey();
      incidentMap.wrapIncidentKey(incidentKey);

      if (incidentMap.getState() == STATE_CREATED) {
        // re-write the failure event with new payload
        failureEvent =
            reader.readValue(incidentMap.getFailureEventPosition(), WorkflowInstanceRecord.class);
        failureEvent.getValue().setPayload(command.getValue().getPayload());

        streamWriter.writeFollowUpEvent(
            failureEvent.getKey(),
            failureEvent.getMetadata().getIntent(),
            failureEvent.getValue(),
            this::setIncidentKey);

        incidentMap.setState(STATE_RESOLVING).write();
      } else {
        streamWriter.writeRejection(
            command, RejectionType.NOT_APPLICABLE, "Incident is not in state CREATED");
      }
    }

    private void setIncidentKey(RecordMetadata metadata) {
      metadata.incidentKey(incidentKey);
    }
  }

  private final class ResolveFailedProcessor implements TypedRecordProcessor<IncidentRecord> {

    @Override
    public void processRecord(
        TypedRecord<IncidentRecord> event,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {
      incidentMap.wrapIncidentKey(event.getKey());

      if (incidentMap.getState() == STATE_RESOLVING) {
        incidentMap.setState(STATE_CREATED).write();
      }
    }
  }

  private final class DeleteIncidentProcessor implements TypedRecordProcessor<IncidentRecord> {
    private TypedStreamReader reader;

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor) {
      reader = streamProcessor.getEnvironment().getStreamReader();
    }

    @Override
    public void processRecord(
        TypedRecord<IncidentRecord> command,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      incidentMap.wrapIncidentKey(command.getKey());

      final long incidentEventPosition = incidentMap.getIncidentEventPosition();

      if (incidentEventPosition > 0) {
        final TypedRecord<IncidentRecord> priorIncidentEvent =
            reader.readValue(incidentEventPosition, IncidentRecord.class);

        streamWriter.writeFollowUpEvent(
            command.getKey(), IncidentIntent.DELETED, priorIncidentEvent.getValue());

        incidentMap.remove(command.getKey());
      } else {
        streamWriter.writeRejection(
            command, RejectionType.NOT_APPLICABLE, "Incident does not exist");
      }
    }
  }

  private final class ActivityRewrittenProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceRecord> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {
      final long incidentKey = record.getMetadata().getIncidentKey();
      if (incidentKey > 0) {
        resolvingEvents.put(record.getPosition(), incidentKey);
      }
    }
  }

  private final class ActivityIncidentResolvedProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {
    private TypedStreamReader reader;

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor) {
      reader = streamProcessor.getEnvironment().getStreamReader();
    }

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      final long incidentKey = resolvingEvents.get(event.getSourcePosition(), -1);
      if (incidentKey > 0) {
        incidentMap.wrapIncidentKey(incidentKey);

        if (incidentMap.getState() == STATE_RESOLVING) {
          // incident is resolved when read next activity lifecycle event
          final long incidentPosition = incidentMap.getIncidentEventPosition();
          final TypedRecord<IncidentRecord> incidentEvent =
              reader.readValue(incidentPosition, IncidentRecord.class);

          streamWriter.writeFollowUpEvent(
              incidentKey, IncidentIntent.RESOLVED, incidentEvent.getValue());

          incidentMap.remove(incidentEvent.getKey());
          activityInstanceMap.remove(incidentEvent.getValue().getElementInstanceKey(), -1L);
          resolvingEvents.remove(event.getSourcePosition(), -1);
        } else {
          throw new IllegalStateException("inconsistent incident map");
        }
      }
    }
  }

  private final class ActivityTerminatedProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {
    private final IncidentRecord incidentEvent = new IncidentRecord();

    @Override
    public void processRecord(
        TypedRecord<WorkflowInstanceRecord> event,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {
      final long incidentKey = activityInstanceMap.get(event.getKey(), -1L);

      if (incidentKey > 0) {
        incidentMap.wrapIncidentKey(incidentKey);

        if (incidentMap.getState() == STATE_CREATED || incidentMap.getState() == STATE_RESOLVING) {
          streamWriter.writeFollowUpCommand(incidentKey, IncidentIntent.DELETE, incidentEvent);

          incidentMap.setState(STATE_DELETING).write();
          activityInstanceMap.remove(event.getKey(), -1L);
        } else {
          throw new IllegalStateException("inconsistent incident map");
        }
      }
    }
  }

  private final class JobFailedProcessor implements TypedRecordProcessor<JobRecord> {
    private final IncidentRecord incidentEvent = new IncidentRecord();

    @Override
    public void processRecord(
        TypedRecord<JobRecord> event,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      final JobRecord value = event.getValue();

      if (value.getRetries() <= 0) {
        final JobHeaders jobHeaders = value.getHeaders();

        incidentEvent.reset();
        incidentEvent
            .setErrorType(ErrorType.JOB_NO_RETRIES)
            .setErrorMessage("No more retries left.")
            .setFailureEventPosition(event.getPosition())
            .setBpmnProcessId(jobHeaders.getBpmnProcessId())
            .setWorkflowInstanceKey(jobHeaders.getWorkflowInstanceKey())
            .setElementId(jobHeaders.getActivityId())
            .setElementInstanceKey(jobHeaders.getActivityInstanceKey())
            .setJobKey(event.getKey());

        failedJobMap.put(event.getKey(), NON_PERSISTENT_INCIDENT);

        if (!event.getMetadata().hasIncidentKey()) {
          streamWriter.writeNewCommand(IncidentIntent.CREATE, incidentEvent);
        } else {
          streamWriter.writeFollowUpEvent(
              event.getMetadata().getIncidentKey(), IncidentIntent.RESOLVE_FAILED, incidentEvent);
        }
      }
    }
  }

  private final class JobIncidentResolvedProcessor implements TypedRecordProcessor<JobRecord> {

    private TypedStreamReader reader;

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor) {
      reader = streamProcessor.getEnvironment().getStreamReader();
    }

    @Override
    public void onClose() {
      reader.close();
    }

    @Override
    public void processRecord(
        TypedRecord<JobRecord> event,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter) {

      final long incidentKey = failedJobMap.get(event.getKey(), -1L);

      if (incidentKey > 0) {
        incidentMap.wrapIncidentKey(incidentKey);

        if (incidentMap.getState() == STATE_CREATED) {
          final TypedRecord<IncidentRecord> persistedIncident =
              reader.readValue(incidentMap.getIncidentEventPosition(), IncidentRecord.class);

          streamWriter.writeFollowUpCommand(
              incidentKey, IncidentIntent.DELETE, persistedIncident.getValue());
          failedJobMap.remove(event.getKey(), -1L);
        } else {
          throw new IllegalStateException("inconsistent incident map");
        }
      } else if (incidentKey == NON_PERSISTENT_INCIDENT) {
        failedJobMap.remove(event.getKey(), -1L);
      }
    }
  }
}
