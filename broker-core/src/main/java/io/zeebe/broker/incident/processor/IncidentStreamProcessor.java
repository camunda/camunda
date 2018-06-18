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
import io.zeebe.broker.job.data.JobHeaders;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.logstreams.processor.CommandProcessor;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.map.Long2LongZbMap;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
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
            .withStateResource(activityInstanceMap)
            .withStateResource(failedJobMap)
            .withStateResource(incidentMap.getMap())
            .withStateResource(resolvingEvents);

    // incident events
    builder =
        builder
            .onCommand(ValueType.INCIDENT, IncidentIntent.CREATE, new CreateIncidentProcessor())
            .onCommand(
                ValueType.INCIDENT, IncidentIntent.RESOLVE, new ResolveIncidentProcessor(env))
            .onEvent(
                ValueType.INCIDENT, IncidentIntent.RESOLVE_FAILED, new ResolveFailedProcessor())
            .onCommand(ValueType.INCIDENT, IncidentIntent.DELETE, new DeleteIncidentProcessor(env));

    // workflow instance events
    final ActivityRewrittenProcessor activityRewrittenProcessor = new ActivityRewrittenProcessor();
    final ActivityIncidentResolvedProcessor activityIncidentResolvedProcessor =
        new ActivityIncidentResolvedProcessor(env);

    builder =
        builder
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.PAYLOAD_UPDATED,
                new PayloadUpdatedProcessor())
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ACTIVITY_TERMINATED,
                new ActivityTerminatedProcessor())
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ACTIVITY_READY,
                activityRewrittenProcessor)
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.GATEWAY_ACTIVATED,
                activityRewrittenProcessor)
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ACTIVITY_COMPLETING,
                activityRewrittenProcessor)
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ACTIVITY_ACTIVATED,
                activityIncidentResolvedProcessor)
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
                activityIncidentResolvedProcessor)
            .onEvent(
                ValueType.WORKFLOW_INSTANCE,
                WorkflowInstanceIntent.ACTIVITY_COMPLETED,
                activityIncidentResolvedProcessor);

    // job events
    final JobIncidentResolvedProcessor jobIncidentResolvedProcessor =
        new JobIncidentResolvedProcessor(env);

    builder =
        builder
            .onEvent(ValueType.JOB, JobIntent.FAILED, new JobFailedProcessor())
            .onEvent(ValueType.JOB, JobIntent.RETRIES_UPDATED, jobIncidentResolvedProcessor)
            .onEvent(ValueType.JOB, JobIntent.CANCELED, jobIncidentResolvedProcessor);

    return builder.build();
  }

  private final class CreateIncidentProcessor implements CommandProcessor<IncidentRecord> {
    private boolean isJobIncident;

    @Override
    public CommandResult onCommand(
        TypedRecord<IncidentRecord> command, CommandControl commandControl) {
      final IncidentRecord incidentEvent = command.getValue();

      isJobIncident = incidentEvent.getJobKey() > 0;

      if (isJobIncident) {
        if (failedJobMap.get(incidentEvent.getJobKey(), -1L) == NON_PERSISTENT_INCIDENT) {
          return commandControl.accept(IncidentIntent.CREATED);
        } else {
          return commandControl.reject(RejectionType.NOT_APPLICABLE, "Job is not failed");
        }
      } else {
        return commandControl.accept(IncidentIntent.CREATED);
      }
    }

    @Override
    public void updateStateOnAccept(TypedRecord<IncidentRecord> command) {
      final IncidentRecord incidentEvent = command.getValue();
      incidentMap
          .newIncident(command.getKey())
          .setState(STATE_CREATED)
          .setIncidentEventPosition(command.getPosition())
          .setFailureEventPosition(incidentEvent.getFailureEventPosition())
          .write();

      if (isJobIncident) {
        failedJobMap.put(incidentEvent.getJobKey(), command.getKey());
      } else {
        activityInstanceMap.put(incidentEvent.getActivityInstanceKey(), command.getKey());
      }
    }
  }

  private final class PayloadUpdatedProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {
    private boolean isResolving;
    private long incidentKey;
    private final IncidentRecord incidentEvent = new IncidentRecord();

    @Override
    public void processRecord(TypedRecord<WorkflowInstanceRecord> event) {
      isResolving = false;

      incidentKey = activityInstanceMap.get(event.getKey(), -1L);

      if (incidentKey > 0 && incidentMap.wrapIncidentKey(incidentKey).getState() == STATE_CREATED) {
        final WorkflowInstanceRecord workflowInstanceEvent = event.getValue();

        incidentEvent.reset();
        incidentEvent
            .setWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
            .setActivityInstanceKey(event.getKey())
            .setPayload(workflowInstanceEvent.getPayload());

        isResolving = true;
      }
    }

    @Override
    public long writeRecord(TypedRecord<WorkflowInstanceRecord> event, TypedStreamWriter writer) {
      return isResolving
          ? writer.writeFollowUpCommand(incidentKey, IncidentIntent.RESOLVE, incidentEvent)
          : 0L;
    }
  }

  private final class ResolveIncidentProcessor implements TypedRecordProcessor<IncidentRecord> {
    private final TypedStreamEnvironment environment;
    private TypedStreamReader reader;

    private boolean resolving;
    private TypedRecord<WorkflowInstanceRecord> failureEvent;
    private long incidentKey;

    ResolveIncidentProcessor(TypedStreamEnvironment environment) {
      this.environment = environment;
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor) {
      reader = environment.getStreamReader();
    }

    @Override
    public void processRecord(TypedRecord<IncidentRecord> command) {
      resolving = false;

      incidentKey = command.getKey();
      incidentMap.wrapIncidentKey(incidentKey);

      resolving = incidentMap.getState() == STATE_CREATED;

      if (resolving) {
        // re-write the failure event with new payload
        failureEvent =
            reader.readValue(incidentMap.getFailureEventPosition(), WorkflowInstanceRecord.class);
        failureEvent.getValue().setPayload(command.getValue().getPayload());
      }
    }

    @Override
    public long writeRecord(TypedRecord<IncidentRecord> command, TypedStreamWriter writer) {
      final long position;

      if (resolving) {
        position =
            writer.writeFollowUpEvent(
                failureEvent.getKey(),
                failureEvent.getMetadata().getIntent(),
                failureEvent.getValue(),
                this::setIncidentKey);
      } else {
        position =
            writer.writeRejection(
                command, RejectionType.NOT_APPLICABLE, "Incident is not in state CREATED");
      }
      return position;
    }

    private void setIncidentKey(RecordMetadata metadata) {
      metadata.incidentKey(incidentKey);
    }

    @Override
    public void updateState(TypedRecord<IncidentRecord> command) {
      if (resolving) {
        incidentMap.setState(STATE_RESOLVING).write();
      }
    }
  }

  private final class ResolveFailedProcessor implements TypedRecordProcessor<IncidentRecord> {
    private boolean isFailed;

    @Override
    public void processRecord(TypedRecord<IncidentRecord> event) {
      incidentMap.wrapIncidentKey(event.getKey());

      isFailed = incidentMap.getState() == STATE_RESOLVING;
    }

    @Override
    public void updateState(TypedRecord<IncidentRecord> event) {
      if (isFailed) {
        incidentMap.setState(STATE_CREATED).write();
      }
    }
  }

  private final class DeleteIncidentProcessor implements TypedRecordProcessor<IncidentRecord> {
    private TypedStreamReader reader;
    private final TypedStreamEnvironment environment;

    private boolean isDeleted;
    private TypedRecord<IncidentRecord> incidentToWrite;

    DeleteIncidentProcessor(TypedStreamEnvironment environment) {
      this.environment = environment;
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor) {
      reader = environment.getStreamReader();
    }

    @Override
    public void processRecord(TypedRecord<IncidentRecord> command) {
      isDeleted = false;

      incidentMap.wrapIncidentKey(command.getKey());

      final long incidentEventPosition = incidentMap.getIncidentEventPosition();
      isDeleted = incidentEventPosition > 0;

      if (isDeleted) {
        final TypedRecord<IncidentRecord> priorIncidentEvent =
            reader.readValue(incidentEventPosition, IncidentRecord.class);

        incidentToWrite = priorIncidentEvent;
      }
    }

    @Override
    public long writeRecord(TypedRecord<IncidentRecord> command, TypedStreamWriter writer) {
      if (isDeleted) {
        return writer.writeFollowUpEvent(
            incidentToWrite.getKey(), IncidentIntent.DELETED, incidentToWrite.getValue());
      } else {
        return writer.writeRejection(
            command, RejectionType.NOT_APPLICABLE, "Incident does not exist");
      }
    }

    @Override
    public void updateState(TypedRecord<IncidentRecord> command) {
      if (isDeleted) {
        incidentMap.remove(command.getKey());
      }
    }
  }

  private final class ActivityRewrittenProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {
    @Override
    public void updateState(TypedRecord<WorkflowInstanceRecord> record) {
      final long incidentKey = record.getMetadata().getIncidentKey();
      if (incidentKey > 0) {
        resolvingEvents.put(record.getPosition(), incidentKey);
      }
    }
  }

  private final class ActivityIncidentResolvedProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {
    private final TypedStreamEnvironment environment;
    private TypedStreamReader reader;

    private boolean isResolved;
    private TypedRecord<IncidentRecord> incidentEvent;

    ActivityIncidentResolvedProcessor(TypedStreamEnvironment environment) {
      this.environment = environment;
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor) {
      reader = environment.getStreamReader();
    }

    @Override
    public void processRecord(TypedRecord<WorkflowInstanceRecord> event) {
      isResolved = false;
      incidentEvent = null;

      final long incidentKey = resolvingEvents.get(event.getSourcePosition(), -1);
      if (incidentKey > 0) {
        incidentMap.wrapIncidentKey(incidentKey);

        if (incidentMap.getState() == STATE_RESOLVING) {
          // incident is resolved when read next activity lifecycle event
          final long incidentPosition = incidentMap.getIncidentEventPosition();
          incidentEvent = reader.readValue(incidentPosition, IncidentRecord.class);

          isResolved = true;
        } else {
          throw new IllegalStateException("inconsistent incident map");
        }
      }
    }

    @Override
    public long writeRecord(TypedRecord<WorkflowInstanceRecord> event, TypedStreamWriter writer) {
      return isResolved
          ? writer.writeFollowUpEvent(
              incidentEvent.getKey(), IncidentIntent.RESOLVED, incidentEvent.getValue())
          : 0L;
    }

    @Override
    public void updateState(TypedRecord<WorkflowInstanceRecord> event) {
      if (isResolved) {
        incidentMap.remove(incidentEvent.getKey());
        activityInstanceMap.remove(incidentEvent.getValue().getActivityInstanceKey(), -1L);
        resolvingEvents.remove(event.getSourcePosition(), -1);
      }
    }
  }

  private final class ActivityTerminatedProcessor
      implements TypedRecordProcessor<WorkflowInstanceRecord> {
    private final IncidentRecord incidentEvent = new IncidentRecord();

    private boolean isTerminated;
    private long incidentKey;

    @Override
    public void processRecord(TypedRecord<WorkflowInstanceRecord> event) {
      isTerminated = false;

      incidentKey = activityInstanceMap.get(event.getKey(), -1L);

      if (incidentKey > 0) {
        incidentMap.wrapIncidentKey(incidentKey);

        if (incidentMap.getState() == STATE_CREATED || incidentMap.getState() == STATE_RESOLVING) {
          isTerminated = true;
        } else {
          throw new IllegalStateException("inconsistent incident map");
        }
      }
    }

    @Override
    public long writeRecord(TypedRecord<WorkflowInstanceRecord> event, TypedStreamWriter writer) {

      return isTerminated
          ? writer.writeFollowUpCommand(incidentKey, IncidentIntent.DELETE, incidentEvent)
          : 0L;
    }

    @Override
    public void updateState(TypedRecord<WorkflowInstanceRecord> event) {
      if (isTerminated) {
        incidentMap.setState(STATE_DELETING).write();
        activityInstanceMap.remove(event.getKey(), -1L);
      }
    }
  }

  private final class JobFailedProcessor implements TypedRecordProcessor<JobRecord> {
    private final IncidentRecord incidentEvent = new IncidentRecord();

    private boolean hasRetries;
    private boolean isResolvingIncident;

    @Override
    public void processRecord(TypedRecord<JobRecord> event) {
      final JobRecord value = event.getValue();
      hasRetries = value.getRetries() > 0;
      isResolvingIncident = event.getMetadata().hasIncidentKey();

      if (!hasRetries) {
        final JobHeaders jobHeaders = value.headers();

        incidentEvent.reset();
        incidentEvent
            .setErrorType(ErrorType.JOB_NO_RETRIES)
            .setErrorMessage("No more retries left.")
            .setFailureEventPosition(event.getPosition())
            .setBpmnProcessId(jobHeaders.getBpmnProcessId())
            .setWorkflowInstanceKey(jobHeaders.getWorkflowInstanceKey())
            .setActivityId(jobHeaders.getActivityId())
            .setActivityInstanceKey(jobHeaders.getActivityInstanceKey())
            .setJobKey(event.getKey());
      }
    }

    @Override
    public long writeRecord(TypedRecord<JobRecord> event, TypedStreamWriter writer) {
      if (hasRetries) {
        return 0L;
      } else {
        if (!isResolvingIncident) {
          return writer.writeNewCommand(IncidentIntent.CREATE, incidentEvent);
        } else {
          return writer.writeFollowUpEvent(
              event.getMetadata().getIncidentKey(), IncidentIntent.RESOLVE_FAILED, incidentEvent);
        }
      }
    }

    @Override
    public void updateState(TypedRecord<JobRecord> event) {
      if (!hasRetries) {
        failedJobMap.put(event.getKey(), NON_PERSISTENT_INCIDENT);
      }
    }
  }

  private final class JobIncidentResolvedProcessor implements TypedRecordProcessor<JobRecord> {
    private final TypedStreamEnvironment environment;

    private TypedStreamReader reader;
    private boolean isResolved;
    private TypedRecord<IncidentRecord> persistedIncident;
    private boolean isTransientIncident;

    JobIncidentResolvedProcessor(TypedStreamEnvironment environment) {
      this.environment = environment;
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor) {
      reader = environment.getStreamReader();
    }

    @Override
    public void onClose() {
      reader.close();
    }

    @Override
    public void processRecord(TypedRecord<JobRecord> event) {
      isResolved = false;
      isTransientIncident = false;

      final long incidentKey = failedJobMap.get(event.getKey(), -1L);
      persistedIncident = null;

      if (incidentKey > 0) {
        incidentMap.wrapIncidentKey(incidentKey);

        if (incidentMap.getState() == STATE_CREATED) {
          persistedIncident =
              reader.readValue(incidentMap.getIncidentEventPosition(), IncidentRecord.class);

          isResolved = true;
        } else {
          throw new IllegalStateException("inconsistent incident map");
        }
      } else if (incidentKey == NON_PERSISTENT_INCIDENT) {
        isTransientIncident = true;
      }
    }

    @Override
    public long writeRecord(TypedRecord<JobRecord> event, TypedStreamWriter writer) {
      return isResolved
          ? writer.writeFollowUpCommand(
              persistedIncident.getKey(), IncidentIntent.DELETE, persistedIncident.getValue())
          : 0L;
    }

    @Override
    public void updateState(TypedRecord<JobRecord> event) {
      if (isResolved || isTransientIncident) {
        failedJobMap.remove(event.getKey(), -1L);
      }
    }
  }
}
