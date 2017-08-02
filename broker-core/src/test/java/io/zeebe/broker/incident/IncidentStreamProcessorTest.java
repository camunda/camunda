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

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import io.zeebe.broker.incident.data.IncidentEvent;
import io.zeebe.broker.incident.data.IncidentState;
import io.zeebe.broker.incident.processor.IncidentStreamProcessor;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.broker.workflow.data.WorkflowInstanceState;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.util.agent.ControllableTaskScheduler;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class IncidentStreamProcessorTest
{
    @Rule
    public ControllableTaskScheduler agentRunnerService = new ControllableTaskScheduler();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private LogStream logStream;
    private LogStreamWriter logStreamWriter;
    private StreamProcessorController streamProcessorController;

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        final String rootPath = tempFolder.getRoot().getAbsolutePath();
        final SnapshotStorage snapshotStorage = LogStreams.createFsSnapshotStore(rootPath).build();

        logStream = LogStreams
                .createFsLogStream(wrapString("test-topic"), 0)
                .logRootPath(rootPath)
                .actorScheduler(agentRunnerService)
                .deleteOnClose(true)
                .build();

        logStream.openAsync();

        final IncidentStreamProcessor incidentStreamProcessor = new IncidentStreamProcessor();
        streamProcessorController = LogStreams
                .createStreamProcessor("incident", 0, incidentStreamProcessor)
                .sourceStream(logStream)
                .targetStream(logStream)
                .snapshotStorage(snapshotStorage)
                .actorScheduler(agentRunnerService)
                .build();

        streamProcessorController.openAsync();

        logStreamWriter = new LogStreamWriterImpl(logStream);
        // all events are committed
        logStream.setCommitPosition(Long.MAX_VALUE);

        agentRunnerService.waitUntilDone();
    }

    @After
    public void cleanUp() throws Exception
    {
        streamProcessorController.closeAsync();
        logStream.closeAsync();
    }

    @Test
    public void shouldNotCreateIncidentIfRetriesAreUpdated()
    {
        // when
        // a failed task has no retries left (=> create incident)
        writeTaskEvent(2L, task -> task
                       .setState(TaskState.FAILED)
                       .setType(wrapString("test"))
                       .setRetries(0));

        // and the retries are updated (=> delete incident)
        writeTaskEvent(2L, task -> task
                       .setState(TaskState.RETRIES_UPDATED)
                       .setType(wrapString("test"))
                       .setRetries(1));

        agentRunnerService.waitUntilDone();

        // then don't create an incident
        assertThat(getIncidentEvents())
            .hasSize(2)
            .extracting("state")
            .containsExactly(IncidentState.CREATE,
                             IncidentState.CREATE_REJECTED);
    }

    @Test
    public void shouldNotResolveIncidentIfActivityTerminated()
    {
        // given
        // an incident for a failed workflow instance
        final long failureEventPosition = writeWorkflowInstanceEvent(2L, wf -> wf
                .setState(WorkflowInstanceState.ACTIVITY_READY)
                .setWorkflowInstanceKey(1L));

        writeIncidentEvent(3L, incident -> incident
               .setState(IncidentState.CREATE)
               .setWorkflowInstanceKey(1L)
               .setActivityInstanceKey(2L)
               .setFailureEventPosition(failureEventPosition));

        agentRunnerService.waitUntilDone();

        // when
        // the payload of the workflow instance is updated (=> resolve incident)
        writeWorkflowInstanceEvent(2L, wf -> wf
                .setState(WorkflowInstanceState.PAYLOAD_UPDATED)
                .setWorkflowInstanceKey(1L));

        // and the workflow instance / activity is terminated (=> delete incident)
        writeWorkflowInstanceEvent(2L, wf -> wf
                                   .setState(WorkflowInstanceState.ACTIVITY_TERMINATED)
                                   .setWorkflowInstanceKey(1L));

        agentRunnerService.waitUntilDone();

        // then don't resolve the incident
        assertThat(getIncidentEvents())
                .hasSize(6)
                .extracting("state")
                .containsExactly(IncidentState.CREATE,
                                 IncidentState.CREATED,
                                 IncidentState.RESOLVE,
                                 IncidentState.DELETE,
                                 IncidentState.RESOLVE_REJECTED,
                                 IncidentState.DELETED);
    }

    private List<IncidentEvent> getIncidentEvents()
    {
        final List<IncidentEvent> incidentEvents = new ArrayList<>();

        final BrokerEventMetadata eventMetadata = new BrokerEventMetadata();

        try (BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream))
        {
            while (logStreamReader.hasNext())
            {
                final LoggedEvent event = logStreamReader.next();

                eventMetadata.reset();
                event.readMetadata(eventMetadata);

                if (eventMetadata.getEventType() == EventType.INCIDENT_EVENT)
                {
                    final MutableDirectBuffer buf = new UnsafeBuffer(new byte[event.getValueLength()]);
                    buf.putBytes(0, event.getValueBuffer(), event.getValueOffset(), event.getValueLength());

                    final IncidentEvent incidentEvent = new IncidentEvent();
                    incidentEvent.wrap(buf);

                    incidentEvents.add(incidentEvent);
                }
            }
            return incidentEvents;
        }
    }

    private long writeTaskEvent(long key, Consumer<TaskEvent> c)
    {
        final BrokerEventMetadata taskMetadata = new BrokerEventMetadata().eventType(EventType.TASK_EVENT);

        final TaskEvent taskEvent = new TaskEvent();
        c.accept(taskEvent);

        return writeEvent(key, taskMetadata, taskEvent);
    }

    private long writeWorkflowInstanceEvent(long key, Consumer<WorkflowInstanceEvent> c)
    {
        final BrokerEventMetadata workflowMetadata = new BrokerEventMetadata().eventType(EventType.WORKFLOW_INSTANCE_EVENT);

        final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();
        c.accept(workflowInstanceEvent);

        return writeEvent(key, workflowMetadata, workflowInstanceEvent);
    }

    private long writeIncidentEvent(long key, Consumer<IncidentEvent> c)
    {
        final BrokerEventMetadata incidentMetadata = new BrokerEventMetadata().eventType(EventType.INCIDENT_EVENT);

        final IncidentEvent incidentEvent = new IncidentEvent();
        c.accept(incidentEvent);

        return writeEvent(key, incidentMetadata, incidentEvent);
    }

    private long writeEvent(long key, final BrokerEventMetadata metadata, final BufferWriter event)
    {
        long position;
        do
        {
            position = logStreamWriter
                    .key(key)
                    .metadataWriter(metadata)
                    .valueWriter(event)
                    .tryWrite();
        }
        while (position < 0);

        return position;
    }

}
