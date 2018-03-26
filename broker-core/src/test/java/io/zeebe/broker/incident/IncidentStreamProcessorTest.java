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
package io.zeebe.broker.incident;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;

import io.zeebe.broker.incident.data.IncidentEvent;
import io.zeebe.broker.incident.data.IncidentState;
import io.zeebe.broker.incident.processor.IncidentStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.broker.workflow.data.WorkflowInstanceState;
import io.zeebe.util.buffer.BufferUtil;

public class IncidentStreamProcessorTest
{
    @Rule
    public StreamProcessorRule rule = new StreamProcessorRule();

    private TypedStreamProcessor buildStreamProcessor(TypedStreamEnvironment env)
    {
        final IncidentStreamProcessor factory = new IncidentStreamProcessor();
        return factory.createStreamProcessor(env);
    }

    /**
     * Event order:
     *
     * Task FAILED -> UPDATE_RETRIES -> RETRIES UPDATED -> Incident CREATE -> Incident CREATE_REJECTED
     */
    @Test
    public void shouldNotCreateIncidentIfRetriesAreUpdatedIntermittently()
    {
        // given
        final TaskEvent task = taskFailed(0);
        final long key = rule.writeEvent(task); // trigger incident creation

        task.setState(TaskState.RETRIES_UPDATED);
        task.setRetries(1);
        rule.writeEvent(key, task); // triggering incident removal

        // when
        rule.runStreamProcessor(this::buildStreamProcessor);

        // then
        waitForEventInState(IncidentState.CREATE_REJECTED);

        final List<TypedEvent<IncidentEvent>> incidentEvents = rule.events().onlyIncidentEvents().collect(Collectors.toList());
        assertThat(incidentEvents).extracting("value.state")
            .containsExactly(
                IncidentState.CREATE,
                IncidentState.CREATE_REJECTED);
    }

    @Test
    public void shouldNotResolveIncidentIfActivityTerminated()
    {
        // given
        final long workflowInstanceKey = 1L;
        final long activityInstanceKey = 2L;

        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);
        control.blockAfterIncidentEvent(e -> e.getValue().getState() == IncidentState.CREATED);

        final WorkflowInstanceEvent activityInstance = new WorkflowInstanceEvent();
        activityInstance.setState(WorkflowInstanceState.ACTIVITY_READY);
        activityInstance.setWorkflowInstanceKey(workflowInstanceKey);

        final long position = rule.writeEvent(activityInstanceKey, activityInstance);

        final IncidentEvent incident = new IncidentEvent();
        incident.setState(IncidentState.CREATE);
        incident.setWorkflowInstanceKey(workflowInstanceKey);
        incident.setActivityInstanceKey(activityInstanceKey);
        incident.setFailureEventPosition(position);

        rule.writeEvent(incident);

        waitForEventInState(IncidentState.CREATED); // stream processor is now blocked

        activityInstance.setState(WorkflowInstanceState.PAYLOAD_UPDATED);
        rule.writeEvent(activityInstanceKey, activityInstance);

        activityInstance.setState(WorkflowInstanceState.ACTIVITY_TERMINATED);
        rule.writeEvent(activityInstanceKey, activityInstance);

        // when
        control.unblock();

        // then
        waitForEventInState(IncidentState.DELETED);
        final List<TypedEvent<IncidentEvent>> incidentEvents = rule.events().onlyIncidentEvents().collect(Collectors.toList());

        assertThat(incidentEvents).extracting("value.state")
            .containsExactly(
                IncidentState.CREATE,
                IncidentState.CREATED,
                IncidentState.RESOLVE,
                IncidentState.DELETE,
                IncidentState.RESOLVE_REJECTED,
                IncidentState.DELETED);
    }

    private TaskEvent taskFailed(int retries)
    {
        final TaskEvent event = new TaskEvent();

        event.setState(TaskState.FAILED);
        event.setRetries(retries);
        event.setType(BufferUtil.wrapString("foo"));

        return event;
    }

    private void waitForEventInState(IncidentState state)
    {
        waitUntil(() -> rule.events().onlyIncidentEvents().inState(state).findFirst().isPresent());
    }
}
