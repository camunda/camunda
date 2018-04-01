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
import io.zeebe.broker.incident.data.IncidentEvent;
import io.zeebe.broker.incident.data.IncidentState;
import io.zeebe.broker.incident.index.IncidentMap;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskHeaders;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.broker.workflow.data.WorkflowInstanceState;
import io.zeebe.map.Long2LongZbMap;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;

/**
 * Is responsible for the incident lifecycle.
 */
public class IncidentStreamProcessor
{
    private static final short STATE_CREATED = 1;
    private static final short STATE_RESOLVING = 2;
    private static final short STATE_DELETING = 3;

    private static final long NON_PERSISTENT_INCIDENT = -2L;

    private final Long2LongZbMap activityInstanceMap = new Long2LongZbMap();
    private final Long2LongZbMap failedTaskMap = new Long2LongZbMap();
    private final IncidentMap incidentMap = new IncidentMap();
    private final Long2LongZbMap resolvingEvents = new Long2LongZbMap();

    public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment env)
    {
        TypedEventStreamProcessorBuilder builder = env.newStreamProcessor()
            .withStateResource(activityInstanceMap)
            .withStateResource(failedTaskMap)
            .withStateResource(incidentMap.getMap())
            .withStateResource(resolvingEvents);


        // incident events
        builder = builder
            .onEvent(EventType.INCIDENT_EVENT, IncidentState.CREATE, new CreateIncidentProcessor())
            .onEvent(EventType.INCIDENT_EVENT, IncidentState.RESOLVE, new ResolveIncidentProcessor(env))
            .onEvent(EventType.INCIDENT_EVENT, IncidentState.RESOLVE_FAILED, new ResolveFailedProcessor())
            .onEvent(EventType.INCIDENT_EVENT, IncidentState.DELETE, new DeleteIncidentProcessor(env));

        // workflow instance events
        final ActivityRewrittenProcessor activityRewrittenProcessor = new ActivityRewrittenProcessor();
        final ActivityIncidentResolvedProcessor activityIncidentResolvedProcessor = new ActivityIncidentResolvedProcessor(env);

        builder = builder
            .onEvent(EventType.WORKFLOW_INSTANCE_EVENT, WorkflowInstanceState.PAYLOAD_UPDATED, new PayloadUpdatedProcessor())
            .onEvent(EventType.WORKFLOW_INSTANCE_EVENT, WorkflowInstanceState.ACTIVITY_TERMINATED, new ActivityTerminatedProcessor())
            .onEvent(EventType.WORKFLOW_INSTANCE_EVENT, WorkflowInstanceState.ACTIVITY_READY, activityRewrittenProcessor)
            .onEvent(EventType.WORKFLOW_INSTANCE_EVENT, WorkflowInstanceState.GATEWAY_ACTIVATED, activityRewrittenProcessor)
            .onEvent(EventType.WORKFLOW_INSTANCE_EVENT, WorkflowInstanceState.ACTIVITY_COMPLETING, activityRewrittenProcessor)
            .onEvent(EventType.WORKFLOW_INSTANCE_EVENT, WorkflowInstanceState.ACTIVITY_ACTIVATED, activityIncidentResolvedProcessor)
            .onEvent(EventType.WORKFLOW_INSTANCE_EVENT, WorkflowInstanceState.SEQUENCE_FLOW_TAKEN, activityIncidentResolvedProcessor)
            .onEvent(EventType.WORKFLOW_INSTANCE_EVENT, WorkflowInstanceState.ACTIVITY_COMPLETED, activityIncidentResolvedProcessor);

        // task events
        final TaskIncidentResolvedProcessor taskIncidentResolvedProcessor = new TaskIncidentResolvedProcessor(env);

        builder = builder
            .onEvent(EventType.TASK_EVENT, TaskState.FAILED, new TaskFailedProcessor())
            .onEvent(EventType.TASK_EVENT, TaskState.RETRIES_UPDATED, taskIncidentResolvedProcessor)
            .onEvent(EventType.TASK_EVENT, TaskState.CANCELED, taskIncidentResolvedProcessor);

        return builder.build();
    }

    private final class CreateIncidentProcessor implements TypedEventProcessor<IncidentEvent>
    {
        private boolean isCreated;
        private boolean isTaskIncident;

        @Override
        public void processEvent(TypedEvent<IncidentEvent> event)
        {
            final IncidentEvent incidentEvent = event.getValue();

            isTaskIncident = incidentEvent.getTaskKey() > 0;
            // ensure that the task is not resolved yet
            isCreated = isTaskIncident ? failedTaskMap.get(incidentEvent.getTaskKey(), -1L) == NON_PERSISTENT_INCIDENT : true;

            if (isCreated)
            {
                incidentEvent.setState(IncidentState.CREATED);
            }
            else
            {
                incidentEvent.setState(IncidentState.CREATE_REJECTED);
            }
        }

        @Override
        public long writeEvent(TypedEvent<IncidentEvent> event, TypedStreamWriter writer)
        {
            return writer.writeFollowupEvent(event.getKey(), event.getValue());
        }

        @Override
        public void updateState(TypedEvent<IncidentEvent> event)
        {
            if (isCreated)
            {
                final IncidentEvent incidentEvent = event.getValue();
                incidentMap
                    .newIncident(event.getKey())
                    .setState(STATE_CREATED)
                    .setIncidentEventPosition(event.getPosition())
                    .setFailureEventPosition(incidentEvent.getFailureEventPosition())
                    .write();

                if (isTaskIncident)
                {
                    failedTaskMap.put(incidentEvent.getTaskKey(), event.getKey());
                }
                else
                {
                    activityInstanceMap.put(incidentEvent.getActivityInstanceKey(), event.getKey());
                }
            }
        }
    }

    private final class PayloadUpdatedProcessor implements TypedEventProcessor<WorkflowInstanceEvent>
    {
        private boolean isResolving;
        private long incidentKey;
        private final IncidentEvent incidentEvent = new IncidentEvent();

        @Override
        public void processEvent(TypedEvent<WorkflowInstanceEvent> event)
        {
            isResolving = false;

            incidentKey = activityInstanceMap.get(event.getKey(), -1L);

            if (incidentKey > 0 && incidentMap.wrapIncidentKey(incidentKey).getState() == STATE_CREATED)
            {
                final WorkflowInstanceEvent workflowInstanceEvent = event.getValue();

                incidentEvent.reset();
                incidentEvent
                    .setState(IncidentState.RESOLVE)
                    .setWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
                    .setActivityInstanceKey(event.getKey())
                    .setPayload(workflowInstanceEvent.getPayload());

                isResolving = true;
            }
        }

        @Override
        public long writeEvent(TypedEvent<WorkflowInstanceEvent> event, TypedStreamWriter writer)
        {
            return isResolving ? writer.writeFollowupEvent(incidentKey, incidentEvent) : 0L;
        }
    }

    private final class ResolveIncidentProcessor implements TypedEventProcessor<IncidentEvent>
    {
        private final TypedStreamEnvironment environment;
        private TypedStreamReader reader;

        private boolean onResolving;
        private TypedEvent<WorkflowInstanceEvent> failureEvent;
        private long incidentKey;

        ResolveIncidentProcessor(TypedStreamEnvironment environment)
        {
            this.environment = environment;
        }

        @Override
        public void onOpen(TypedStreamProcessor streamProcessor)
        {
            reader = environment.getStreamReader();
        }

        @Override
        public void processEvent(TypedEvent<IncidentEvent> event)
        {
            onResolving = false;

            incidentKey = event.getKey();
            incidentMap.wrapIncidentKey(incidentKey);

            if (incidentMap.getState() == STATE_CREATED)
            {
                // re-write the failure event with new payload
                failureEvent = reader.readValue(incidentMap.getFailureEventPosition(), WorkflowInstanceEvent.class);
                failureEvent.getValue().setPayload(event.getValue().getPayload());

                onResolving = true;
            }
            else
            {
                event.getValue().setState(IncidentState.RESOLVE_REJECTED);
            }
        }

        @Override
        public long writeEvent(TypedEvent<IncidentEvent> event, TypedStreamWriter writer)
        {
            final long position;

            if (onResolving)
            {
                position = writer.writeFollowupEvent(
                    failureEvent.getKey(),
                    failureEvent.getValue(),
                    this::setIncidentKey);
            }
            else
            {
                position = writer.writeFollowupEvent(event.getKey(), event.getValue());
            }
            return position;
        }

        private void setIncidentKey(BrokerEventMetadata metadata)
        {
            metadata.incidentKey(incidentKey);
        }

        @Override
        public void updateState(TypedEvent<IncidentEvent> event)
        {
            if (onResolving)
            {
                incidentMap
                    .setState(STATE_RESOLVING)
                    .write();
            }
        }
    }

    private final class ResolveFailedProcessor implements TypedEventProcessor<IncidentEvent>
    {
        private boolean isFailed;

        @Override
        public void processEvent(TypedEvent<IncidentEvent> event)
        {
            incidentMap.wrapIncidentKey(event.getKey());

            isFailed = incidentMap.getState() == STATE_RESOLVING;
        }

        @Override
        public void updateState(TypedEvent<IncidentEvent> event)
        {
            if (isFailed)
            {
                incidentMap
                    .setState(STATE_CREATED)
                    .write();
            }
        }
    }

    private final class DeleteIncidentProcessor implements TypedEventProcessor<IncidentEvent>
    {
        private TypedStreamReader reader;
        private final TypedStreamEnvironment environment;

        private boolean isDeleted;
        private TypedEvent<IncidentEvent> incidentToWrite;

        DeleteIncidentProcessor(TypedStreamEnvironment environment)
        {
            this.environment = environment;
        }

        @Override
        public void onOpen(TypedStreamProcessor streamProcessor)
        {
            reader = environment.getStreamReader();
        }

        @Override
        public void processEvent(TypedEvent<IncidentEvent> event)
        {
            isDeleted = false;

            incidentMap.wrapIncidentKey(event.getKey());

            final long incidentEventPosition = incidentMap.getIncidentEventPosition();

            if (incidentEventPosition > 0)
            {
                final TypedEvent<IncidentEvent> priorIncidentEvent =
                        reader.readValue(incidentEventPosition, IncidentEvent.class);

                priorIncidentEvent.getValue().setState(IncidentState.DELETED);
                incidentToWrite = priorIncidentEvent;
                isDeleted = true;
            }
            else
            {
                event.getValue().setState(IncidentState.DELETE_REJECTED);
                incidentToWrite = event;
            }
        }

        @Override
        public long writeEvent(TypedEvent<IncidentEvent> event, TypedStreamWriter writer)
        {
            return writer.writeFollowupEvent(incidentToWrite.getKey(), incidentToWrite.getValue());
        }

        @Override
        public void updateState(TypedEvent<IncidentEvent> event)
        {
            if (isDeleted)
            {
                incidentMap.remove(event.getKey());
            }
        }
    }

    private final class ActivityRewrittenProcessor implements TypedEventProcessor<WorkflowInstanceEvent>
    {
        @Override
        public void updateState(TypedEvent<WorkflowInstanceEvent> event)
        {
            final long incidentKey = event.getMetadata().getIncidentKey();
            if (incidentKey > 0)
            {
                resolvingEvents.put(event.getPosition(), incidentKey);
            }
        }
    }

    private final class ActivityIncidentResolvedProcessor implements TypedEventProcessor<WorkflowInstanceEvent>
    {
        private final TypedStreamEnvironment environment;
        private TypedStreamReader reader;

        private boolean isResolved;
        private TypedEvent<IncidentEvent> incidentEvent;

        ActivityIncidentResolvedProcessor(TypedStreamEnvironment environment)
        {
            this.environment = environment;
        }

        @Override
        public void onOpen(TypedStreamProcessor streamProcessor)
        {
            reader = environment.getStreamReader();
        }

        @Override
        public void processEvent(TypedEvent<WorkflowInstanceEvent> event)
        {
            isResolved = false;
            incidentEvent = null;

            final long incidentKey = resolvingEvents.get(event.getSourcePosition(), -1);
            if (incidentKey > 0)
            {
                incidentMap.wrapIncidentKey(incidentKey);

                if (incidentMap.getState() == STATE_RESOLVING)
                {
                    // incident is resolved when read next activity lifecycle event
                    final long incidentPosition = incidentMap.getIncidentEventPosition();
                    incidentEvent = reader.readValue(incidentPosition, IncidentEvent.class);

                    incidentEvent.getValue().setState(IncidentState.RESOLVED);

                    isResolved = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident map");
                }
            }
        }

        @Override
        public long writeEvent(TypedEvent<WorkflowInstanceEvent> event, TypedStreamWriter writer)
        {
            return isResolved ?
                    writer.writeFollowupEvent(incidentEvent.getKey(), incidentEvent.getValue())
                    : 0L;
        }

        @Override
        public void updateState(TypedEvent<WorkflowInstanceEvent> event)
        {
            if (isResolved)
            {
                incidentMap.remove(incidentEvent.getKey());
                activityInstanceMap.remove(incidentEvent.getValue().getActivityInstanceKey(), -1L);
                resolvingEvents.remove(event.getSourcePosition(), -1);
            }
        }
    }

    private final class ActivityTerminatedProcessor implements TypedEventProcessor<WorkflowInstanceEvent>
    {
        private final IncidentEvent incidentEvent = new IncidentEvent();

        private boolean isTerminated;
        private long incidentKey;


        @Override
        public void processEvent(TypedEvent<WorkflowInstanceEvent> event)
        {
            isTerminated = false;

            incidentKey = activityInstanceMap.get(event.getKey(), -1L);

            if (incidentKey > 0)
            {
                incidentMap.wrapIncidentKey(incidentKey);

                if (incidentMap.getState() == STATE_CREATED || incidentMap.getState() == STATE_RESOLVING)
                {
                    incidentEvent.setState(IncidentState.DELETE);

                    isTerminated = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident map");
                }
            }
        }

        @Override
        public long writeEvent(TypedEvent<WorkflowInstanceEvent> event, TypedStreamWriter writer)
        {

            return isTerminated ?
                    writer.writeFollowupEvent(incidentKey, incidentEvent)
                    : 0L;
        }

        @Override
        public void updateState(TypedEvent<WorkflowInstanceEvent> event)
        {
            if (isTerminated)
            {
                incidentMap.setState(STATE_DELETING).write();
                activityInstanceMap.remove(event.getKey(), -1L);
            }
        }
    }

    private final class TaskFailedProcessor implements TypedEventProcessor<TaskEvent>
    {
        private final IncidentEvent incidentEvent = new IncidentEvent();

        private boolean hasRetries;

        @Override
        public void processEvent(TypedEvent<TaskEvent> event)
        {
            final TaskEvent value = event.getValue();
            hasRetries = value.getRetries() > 0;

            if (!hasRetries)
            {
                final TaskHeaders taskHeaders = value.headers();

                incidentEvent.reset();
                incidentEvent
                    .setState(IncidentState.CREATE)
                    .setErrorType(ErrorType.TASK_NO_RETRIES)
                    .setErrorMessage("No more retries left.")
                    .setFailureEventPosition(event.getPosition())
                    .setBpmnProcessId(taskHeaders.getBpmnProcessId())
                    .setWorkflowInstanceKey(taskHeaders.getWorkflowInstanceKey())
                    .setActivityId(taskHeaders.getActivityId())
                    .setActivityInstanceKey(taskHeaders.getActivityInstanceKey())
                    .setTaskKey(event.getKey());
            }
        }

        @Override
        public long writeEvent(TypedEvent<TaskEvent> event, TypedStreamWriter writer)
        {
            return hasRetries ?
                0L :
                writer.writeNewEvent(incidentEvent);
        }

        @Override
        public void updateState(TypedEvent<TaskEvent> event)
        {
            if (!hasRetries)
            {
                failedTaskMap.put(event.getKey(), NON_PERSISTENT_INCIDENT);
            }
        }
    }

    private final class TaskIncidentResolvedProcessor implements TypedEventProcessor<TaskEvent>
    {
        private final TypedStreamEnvironment environment;

        private TypedStreamReader reader;
        private boolean isResolved;
        private TypedEvent<IncidentEvent> persistedIncident;
        private boolean isTransientIncident;

        TaskIncidentResolvedProcessor(TypedStreamEnvironment environment)
        {
            this.environment = environment;
        }

        @Override
        public void onOpen(TypedStreamProcessor streamProcessor)
        {
            reader = environment.getStreamReader();
        }

        @Override
        public void onClose()
        {
            reader.close();
        }

        @Override
        public void processEvent(TypedEvent<TaskEvent> event)
        {
            isResolved = false;
            isTransientIncident = false;

            final long incidentKey = failedTaskMap.get(event.getKey(), -1L);
            persistedIncident = null;

            if (incidentKey > 0)
            {
                incidentMap.wrapIncidentKey(incidentKey);

                if (incidentMap.getState() == STATE_CREATED)
                {
                    persistedIncident = reader.readValue(incidentMap.getIncidentEventPosition(), IncidentEvent.class);

                    persistedIncident.getValue().setState(IncidentState.DELETE);

                    isResolved = true;
                }
                else
                {
                    throw new IllegalStateException("inconsistent incident map");
                }
            }
            else if (incidentKey == NON_PERSISTENT_INCIDENT)
            {
                isTransientIncident = true;
            }
        }

        @Override
        public long writeEvent(TypedEvent<TaskEvent> event, TypedStreamWriter writer)
        {
            return isResolved ?
                    writer.writeFollowupEvent(persistedIncident.getKey(), persistedIncident.getValue()) :
                    0L;
        }

        @Override
        public void updateState(TypedEvent<TaskEvent> event)
        {
            if (isResolved || isTransientIncident)
            {
                failedTaskMap.remove(event.getKey(), -1L);
            }
        }
    }

}
