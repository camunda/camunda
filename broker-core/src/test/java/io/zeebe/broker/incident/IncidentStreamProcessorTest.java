/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.hashindex.store.FileChannelIndexStore.tempFileIndexStore;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.broker.incident.data.IncidentEvent;
import io.zeebe.broker.incident.data.IncidentEventType;
import io.zeebe.broker.incident.processor.IncidentStreamProcessor;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskEventType;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.broker.workflow.data.WorkflowInstanceEventType;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.util.agent.ControllableTaskScheduler;
import io.zeebe.util.buffer.BufferWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
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

        final IncidentStreamProcessor incidentStreamProcessor = new IncidentStreamProcessor(tempFileIndexStore(), tempFileIndexStore(), tempFileIndexStore());
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
                       .setEventType(TaskEventType.FAILED)
                       .setType(wrapString("test"))
                       .setRetries(0));

        // and the retries are updated (=> delete incident)
        writeTaskEvent(2L, task -> task
                       .setEventType(TaskEventType.RETRIES_UPDATED)
                       .setType(wrapString("test"))
                       .setRetries(1));

        agentRunnerService.waitUntilDone();

        // then don't create an incident
        assertThat(getIncidentEvents())
            .hasSize(2)
            .extracting("eventType")
            .containsExactly(IncidentEventType.CREATE,
                             IncidentEventType.CREATE_REJECTED);
    }

    @Test
    public void shouldNotResolveIncidentIfActivityTerminated()
    {
        // given
        // an incident for a failed workflow instance
        final long failureEventPosition = writeWorkflowInstanceEvent(2L, wf -> wf
                .setEventType(WorkflowInstanceEventType.ACTIVITY_READY)
                .setWorkflowInstanceKey(1L));

        writeIncidentEvent(3L, incident -> incident
               .setEventType(IncidentEventType.CREATE)
               .setWorkflowInstanceKey(1L)
               .setActivityInstanceKey(2L)
               .setFailureEventPosition(failureEventPosition));

        agentRunnerService.waitUntilDone();

        // when
        // the payload of the workflow instance is updated (=> resolve incident)
        writeWorkflowInstanceEvent(2L, wf -> wf
                .setEventType(WorkflowInstanceEventType.PAYLOAD_UPDATED)
                .setWorkflowInstanceKey(1L));

        // and the workflow instance / activity is terminated (=> delete incident)
        writeWorkflowInstanceEvent(2L, wf -> wf
                                   .setEventType(WorkflowInstanceEventType.ACTIVITY_TERMINATED)
                                   .setWorkflowInstanceKey(1L));

        agentRunnerService.waitUntilDone();

        // then don't resolve the incident
        assertThat(getIncidentEvents())
                .hasSize(6)
                .extracting("eventType")
                .containsExactly(IncidentEventType.CREATE,
                                 IncidentEventType.CREATED,
                                 IncidentEventType.RESOLVE,
                                 IncidentEventType.DELETE,
                                 IncidentEventType.RESOLVE_REJECTED,
                                 IncidentEventType.DELETED);
    }

    private List<IncidentEvent> getIncidentEvents()
    {
        final List<IncidentEvent> incidentEvents = new ArrayList<>();

        final BrokerEventMetadata eventMetadata = new BrokerEventMetadata();

        final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream);
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

    private long writeTaskEvent(long key, Consumer<TaskEvent> c)
    {
        final BrokerEventMetadata taskMetadata = new BrokerEventMetadata().eventType(EventType.TASK_EVENT);

        final TaskEvent taskEvent = new TaskEvent();
        c.accept(taskEvent);

        return writeEvent(key, taskMetadata, taskEvent);
    }

    private long writeWorkflowInstanceEvent(long key, Consumer<WorkflowInstanceEvent> c)
    {
        final BrokerEventMetadata workflowMetadata = new BrokerEventMetadata().eventType(EventType.WORKFLOW_EVENT);

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
