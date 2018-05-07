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

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;

import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.incident.processor.IncidentStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.task.data.TaskRecord;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.TaskIntent;
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
        final TaskRecord task = task(0);
        final long key = rule.writeEvent(TaskIntent.FAILED, task); // trigger incident creation

        task.setRetries(1);
        rule.writeEvent(key, TaskIntent.RETRIES_UPDATED, task); // triggering incident removal

        // when
        rule.runStreamProcessor(this::buildStreamProcessor);

        // then
        waitForRejectionWithIntent(IncidentIntent.CREATE);

        final List<TypedRecord<IncidentRecord>> incidentEvents = rule.events().onlyIncidentRecords().collect(Collectors.toList());
        assertThat(incidentEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                tuple(RecordType.COMMAND, IncidentIntent.CREATE),
                tuple(RecordType.COMMAND_REJECTION, IncidentIntent.CREATE));
    }

    @Test
    public void shouldNotResolveIncidentIfActivityTerminated()
    {
        // given
        final long workflowInstanceKey = 1L;
        final long activityInstanceKey = 2L;

        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);
        control.blockAfterIncidentEvent(e -> e.getMetadata().getIntent() == IncidentIntent.CREATED);

        final WorkflowInstanceRecord activityInstance = new WorkflowInstanceRecord();
        activityInstance.setWorkflowInstanceKey(workflowInstanceKey);

        final long position = rule.writeEvent(activityInstanceKey, WorkflowInstanceIntent.ACTIVITY_READY, activityInstance);

        final IncidentRecord incident = new IncidentRecord();
        incident.setWorkflowInstanceKey(workflowInstanceKey);
        incident.setActivityInstanceKey(activityInstanceKey);
        incident.setFailureEventPosition(position);

        rule.writeCommand(IncidentIntent.CREATE, incident);

        waitForEventWithIntent(IncidentIntent.CREATED); // stream processor is now blocked

        rule.writeEvent(activityInstanceKey, WorkflowInstanceIntent.PAYLOAD_UPDATED, activityInstance);
        rule.writeEvent(activityInstanceKey, WorkflowInstanceIntent.ACTIVITY_TERMINATED, activityInstance);

        // when
        control.unblock();

        // then
        waitForEventWithIntent(IncidentIntent.DELETED);
        final List<TypedRecord<IncidentRecord>> incidentEvents = rule.events().onlyIncidentRecords().collect(Collectors.toList());

        assertThat(incidentEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                tuple(RecordType.COMMAND, IncidentIntent.CREATE),
                tuple(RecordType.EVENT, IncidentIntent.CREATED),
                tuple(RecordType.COMMAND, IncidentIntent.RESOLVE),
                tuple(RecordType.COMMAND, IncidentIntent.DELETE),
                tuple(RecordType.COMMAND_REJECTION, IncidentIntent.RESOLVE),
                tuple(RecordType.EVENT, IncidentIntent.DELETED));
    }

    private TaskRecord task(int retries)
    {
        final TaskRecord event = new TaskRecord();

        event.setRetries(retries);
        event.setType(BufferUtil.wrapString("foo"));

        return event;
    }

    private void waitForEventWithIntent(Intent state)
    {
        waitUntil(() -> rule.events().onlyIncidentRecords().onlyEvents().withIntent(state).findFirst().isPresent());
    }

    private void waitForRejectionWithIntent(Intent state)
    {
        waitUntil(() -> rule.events().onlyIncidentRecords().onlyRejections().withIntent(state).findFirst().isPresent());
    }
}
