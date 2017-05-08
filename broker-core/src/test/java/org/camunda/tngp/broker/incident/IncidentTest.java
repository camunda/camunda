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
package org.camunda.tngp.broker.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.encodeMsgPack;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.taskEvents;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;
import static org.camunda.tngp.util.buffer.BufferUtil.wrapString;

import java.util.function.Predicate;

import org.agrona.MutableDirectBuffer;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.incident.data.ErrorType;
import org.camunda.tngp.broker.incident.data.IncidentEventType;
import org.camunda.tngp.broker.protocol.clientapi.EmbeddedBrokerRule;
import org.camunda.tngp.msgpack.spec.MsgPackHelper;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class IncidentTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient testClient;

    private static final BpmnModelInstance WORKFLOW_INPUT_MAPPING = wrap(
            Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("failingTask")
            .done())
            .taskDefinition("failingTask", "test", 3)
            .ioMapping("failingTask")
                .input("$.foo", "$.foo")
                .done();

    private static final BpmnModelInstance WORKFLOW_OUTPUT_MAPPING = wrap(
            Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("failingTask")
            .done())
            .taskDefinition("failingTask", "test", 3)
            .ioMapping("failingTask")
                .output("$.foo", "$.foo")
                .done();

    private static final byte[] PAYLOAD;

    static
    {
        final MutableDirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            w.writeString(wrapString("foo"));
            w.writeString(wrapString("bar"));
        });
        PAYLOAD = new byte[buffer.capacity()];
        buffer.getBytes(0, PAYLOAD);
    }

    @Before
    public void init() throws Exception
    {
        testClient = apiRule.topic();
    }

    @Test
    public void shouldCreateIncidentForInputMappingFailure()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query '$.foo'.")
            .containsEntry("failureEventPosition", failureEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldCreateIncidentForOutputMappingFailure()
    {
        // given
        testClient.deploy(WORKFLOW_OUTPUT_MAPPING);

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeTaskOfType("test");

        // then
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(taskEvents("COMPLETED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query '$.foo'.")
            .containsEntry("failureEventPosition", failureEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldResolveIncidentForInputMappingFailure() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        final ExecuteCommandResponse response = resolveIncident(incidentEvent.key(), PAYLOAD);

        // then
        assertThat(response.key()).isEqualTo(incidentEvent.key());
        assertThat(response.getEvent()).containsEntry("eventType", IncidentEventType.RESOLVED.name());

        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(taskEvents("CREATED"));
        assertThat(followUpEvent.event()).containsEntry("payload", PAYLOAD);

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query '$.foo'.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldResolveIncidentForOutputMappingFailure() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_OUTPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeTaskOfType("test");

        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        final ExecuteCommandResponse response = resolveIncident(incidentEvent.key(), PAYLOAD);

        // then
        assertThat(response.key()).isEqualTo(incidentEvent.key());
        assertThat(response.getEvent()).containsEntry("eventType", IncidentEventType.RESOLVED.name());

        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));
        assertThat(followUpEvent.event()).containsEntry("payload", PAYLOAD);

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query '$.foo'.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldRejectToResolveNotExistingIncident() throws Exception
    {
        // when
        final ExecuteCommandResponse response = resolveIncident(-1L, PAYLOAD);

        // then
        assertThat(response.getEvent()).containsEntry("eventType", IncidentEventType.RESOLVE_REJECTED.name());

        testClient.receiveSingleEvent(incidentEvents("RESOLVE_REJECTED"));
    }

    @Test
    public void shouldFailToResolveIncident() throws Exception
    {
        // given
        final BpmnModelInstance modelInstance = wrap(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("failingTask")
                    .done())
                    .taskDefinition("failingTask", "test", 3)
                    .ioMapping("failingTask")
                        .input("$.foo", "$.foo")
                        .input("$.bar", "$.bar")
                        .done();

        testClient.deploy(modelInstance);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));
        assertThat(incidentEvent.event()).containsEntry("errorMessage", "No data found for query '$.foo'.");

        // when
        final ExecuteCommandResponse response = resolveIncident(incidentEvent.key(), PAYLOAD);

        // then
        assertThat(response.key()).isEqualTo(incidentEvent.key());
        assertThat(response.getEvent())
            .containsEntry("eventType", IncidentEventType.RESOLVE_FAILED.name())
            .containsEntry("errorMessage", "No data found for query '$.bar'.");

        final SubscribedEvent resolveFailedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVE_FAILED"));
        assertThat(resolveFailedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(resolveFailedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query '$.bar'.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldResolveIncidentAfterPreviousResolvingFailed() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        resolveIncident(incidentEvent.key(), MsgPackHelper.EMTPY_OBJECT);

        testClient.receiveSingleEvent(incidentEvents("RESOLVE_FAILED"));

        // when
        resolveIncident(incidentEvent.key(), PAYLOAD);

        // then
        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query '$.foo'.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldCreateIncidentIfTaskHasNoRetriesLeft()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        // when
        failTaskWithNoRetriesLeft();

        // then
        final SubscribedEvent failedEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("failureEventPosition", failedEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldDeleteIncidentIfTaskRetriesIncreased()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        failTaskWithNoRetriesLeft();

        // when
        final SubscribedEvent taskEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicName(ClientApiRule.DEFAULT_TOPIC_NAME)
            .partitionId(ClientApiRule.DEFAULT_PARTITION_ID)
            .key(taskEvent.key())
            .eventTypeTask()
            .command()
                .put("eventType", "UPDATE_RETRIES")
                .put("retries", 1)
                .put("type", "test")
                .put("lockOwner", taskEvent.event().get("lockOwner"))
                .put("headers", taskEvent.event().get("headers"))
                .done()
            .sendAndAwait();

        assertThat(response.getEvent()).containsEntry("eventType", "RETRIES_UPDATED");

        // then
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldRejectToResolveTaskIncident() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        testClient.createWorkflowInstance("process", PAYLOAD);

        failTaskWithNoRetriesLeft();

        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        final ExecuteCommandResponse response = resolveIncident(incidentEvent.key(), PAYLOAD);

        // then
        assertThat(response.getEvent()).containsEntry("eventType", IncidentEventType.RESOLVE_REJECTED.name());

        testClient.receiveSingleEvent(incidentEvents("RESOLVE_REJECTED"));
    }

    private void failTaskWithNoRetriesLeft()
    {
        apiRule.openTaskSubscription(ClientApiRule.DEFAULT_TOPIC_NAME, ClientApiRule.DEFAULT_PARTITION_ID, "test").await();

        final SubscribedEvent taskEvent = testClient.receiveSingleEvent(taskEvents("LOCKED"));

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicName(ClientApiRule.DEFAULT_TOPIC_NAME)
            .partitionId(ClientApiRule.DEFAULT_PARTITION_ID)
            .key(taskEvent.key())
            .eventTypeTask()
            .command()
                .put("eventType", "FAIL")
                .put("retries", 0)
                .put("type", "test")
                .put("lockOwner", taskEvent.event().get("lockOwner"))
                .put("headers", taskEvent.event().get("headers"))
                .done()
            .sendAndAwait();

        assertThat(response.getEvent()).containsEntry("eventType", "FAILED");
    }

    private ExecuteCommandResponse resolveIncident(long incidentKey, byte[] payload)
    {
        return apiRule.createCmdRequest()
            .topicName(ClientApiRule.DEFAULT_TOPIC_NAME)
            .partitionId(ClientApiRule.DEFAULT_PARTITION_ID)
            .eventType(EventType.INCIDENT_EVENT)
            .key(incidentKey)
            .command()
                .put("eventType", IncidentEventType.RESOLVE)
                .put("payload", payload)
                .done()
            .sendAndAwait();
    }

    private static Predicate<SubscribedEvent> incidentEvents(String eventType)
    {
        return e -> e.eventType() == EventType.INCIDENT_EVENT && eventType.equals(e.event().get("eventType"));
    }

}
