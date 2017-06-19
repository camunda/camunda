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
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.*;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.JSON_MAPPER;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.MSGPACK_MAPPER;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.incidentEvents;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.taskEvents;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;
import static org.camunda.tngp.util.buffer.BufferUtil.wrapString;

import java.io.IOException;

import org.agrona.MutableDirectBuffer;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.incident.data.ErrorType;
import org.camunda.tngp.broker.test.EmbeddedBrokerRule;
import org.camunda.tngp.broker.workflow.data.WorkflowInstanceEventType;
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
    private static final String PROP_PAYLOAD = "payload";

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
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("failureEventPosition", failureEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", failureEvent.key())
            .containsEntry("taskKey", -1);
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
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETING"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("failureEventPosition", failureEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", failureEvent.key())
            .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldResolveIncidentForInputMappingFailure() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        assertThat(followUpEvent.event()).containsEntry("payload", PAYLOAD);

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", followUpEvent.key())
            .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldResolveIncidentForOutputMappingFailure() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW_OUTPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeTaskOfType("test");

        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETING"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));
        assertThat(followUpEvent.event()).containsEntry("payload", PAYLOAD);

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", followUpEvent.key())
            .containsEntry("taskKey", -1);
    }


    @Test
    public void shouldCreateIncidentForInvalidResultOnInputMapping() throws Throwable
    {
        // given
        testClient.deploy(wrap(Bpmn.createExecutableProcess("process")
                                   .startEvent()
                                   .serviceTask("service")
                                   .endEvent()
                                   .done()).taskDefinition("service", "external", 5)
                                           .ioMapping("service")
                                           .input("$.string", "$")
                                           .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // then incident is created
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "Processing failed, since mapping will result in a non map object (json object).");
    }

    @Test
    public void shouldResolveIncidentForInvalidResultOnInputMapping() throws Throwable
    {
        // given
        testClient.deploy(wrap(Bpmn.createExecutableProcess("process")
                                   .startEvent()
                                   .serviceTask("service")
                                   .endEvent()
                                   .done()).taskDefinition("service", "external", 5)
                                           .ioMapping("service")
                                           .input("$.string", "$")
                                           .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // then incident is created
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'string':{'obj':'test'}}");

        // then
        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        final byte[] result = (byte[]) followUpEvent.event()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Processing failed, since mapping will result in a non map object (json object).")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("taskKey", -1);
    }

    @Test
    public void shouldCreateIncidentForInvalidResultOnOutputMapping() throws Throwable
    {
        // given
        testClient.deploy(wrap(Bpmn.createExecutableProcess("process")
                                   .startEvent()
                                   .serviceTask("service")
                                   .endEvent()
                                   .done()).taskDefinition("service", "external", 5)
                                           .ioMapping("service")
                                           .input("$.jsonObject", "$")
                                           .output("$.testAttr", "$")
                                           .done());
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'testAttr':'test'}")));
        testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        // then incident is created
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                         .containsEntry("errorMessage", "Processing failed, since mapping will result in a non map object (json object).");
    }

    @Test
    public void shouldResolveIncidentForInvalidResultOnOutputMapping() throws Throwable
    {
        // given
        testClient.deploy(wrap(Bpmn.createExecutableProcess("process")
                                   .startEvent()
                                   .serviceTask("service")
                                   .endEvent()
                                   .done()).taskDefinition("service", "external", 5)
                                           .ioMapping("service")
                                           .input("$.jsonObject", "$")
                                           .output("$.testAttr", "$")
                                           .done());
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'testAttr':'test'}")));
        testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        // then incident is created
        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETING"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent, "{'testAttr':{'obj':'test'}}");

        // then
        final SubscribedEvent followUpEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        final byte[] result = (byte[]) followUpEvent.event()
                                                    .get(PROP_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'obj':'test'}"));

        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event()).containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
                                                 .containsEntry("errorMessage",
                                                                "Processing failed, since mapping will result in a non map object (json object).")
                                                 .containsEntry("bpmnProcessId", "process")
                                                 .containsEntry("workflowInstanceKey", workflowInstanceKey)
                                                 .containsEntry("activityId", "service")
                                                 .containsEntry("activityInstanceKey", followUpEvent.key())
                                                 .containsEntry("taskKey", -1);
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

        final  SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));
        assertThat(incidentEvent.event()).containsEntry("errorMessage", "No data found for query $.foo.");

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedEvent resolveFailedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVE_FAILED"));
        assertThat(resolveFailedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(resolveFailedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.bar.")
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

        final SubscribedEvent failureEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_READY"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        updatePayload(workflowInstanceKey, failureEvent.key(), MsgPackHelper.EMTPY_OBJECT);

        testClient.receiveSingleEvent(incidentEvents("RESOLVE_FAILED"));

        // when
        updatePayload(workflowInstanceKey, failureEvent.key(), PAYLOAD);

        // then
        final SubscribedEvent incidentResolvedEvent = testClient.receiveSingleEvent(incidentEvents("RESOLVED"));
        assertThat(incidentResolvedEvent.key()).isEqualTo(incidentEvent.key());
        assertThat(incidentResolvedEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask");
    }

    @Test
    public void shouldDeleteIncidentIfActivityTerminated()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent incidentCreatedEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        cancelWorkflowInstance(workflowInstanceKey);

        // then
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETED"));

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.foo.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.event().get("activityInstanceKey"));
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
        final SubscribedEvent activityEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent failedEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("failureEventPosition", failedEvent.position())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", activityEvent.key())
            .containsEntry("taskKey", failedEvent.key());
    }

    @Test
    public void shouldDeleteIncidentIfTaskRetriesIncreased()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        failTaskWithNoRetriesLeft();

        // when
        updateTaskRetries();

        // then
        final SubscribedEvent taskEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));
        final SubscribedEvent activityEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", activityEvent.key())
            .containsEntry("taskKey", taskEvent.key());
    }

    @Test
    public void shouldDeleteIncidentIfTaskIsCanceled()
    {
        // given
        testClient.deploy(WORKFLOW_INPUT_MAPPING);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

        failTaskWithNoRetriesLeft();

        final SubscribedEvent incidentCreatedEvent = testClient.receiveSingleEvent(incidentEvents("CREATED"));

        // when
        cancelWorkflowInstance(workflowInstanceKey);

        // then
        final SubscribedEvent taskEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETED"));

        assertThat(incidentEvent.key()).isEqualTo(incidentCreatedEvent.key());
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "failingTask")
            .containsEntry("activityInstanceKey", incidentEvent.event().get("activityInstanceKey"))
            .containsEntry("taskKey", taskEvent.key());
    }

    @Test
    public void shouldCreateIncidentIfStandaloneTaskHasNoRetriesLeft()
    {
        // given
        createStandaloneTask();

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
            .containsEntry("bpmnProcessId", "")
            .containsEntry("workflowInstanceKey", -1)
            .containsEntry("activityId", "")
            .containsEntry("activityInstanceKey", -1)
            .containsEntry("taskKey", failedEvent.key());
    }

    @Test
    public void shouldDeleteStandaloneIncidentIfTaskRetriesIncreased()
    {
        // given
        createStandaloneTask();

        failTaskWithNoRetriesLeft();

        // when
        updateTaskRetries();

        // then
        final SubscribedEvent taskEvent = testClient.receiveSingleEvent(taskEvents("FAILED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("DELETED"));

        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.TASK_NO_RETRIES.name())
            .containsEntry("errorMessage", "No more retries left.")
            .containsEntry("bpmnProcessId", "")
            .containsEntry("workflowInstanceKey", -1)
            .containsEntry("activityId", "")
            .containsEntry("activityInstanceKey", -1)
            .containsEntry("taskKey", taskEvent.key());
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

    private void createStandaloneTask()
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicName(ClientApiRule.DEFAULT_TOPIC_NAME)
            .partitionId(ClientApiRule.DEFAULT_PARTITION_ID)
            .eventTypeTask()
            .command()
                .put("eventType", "CREATE")
                .put("type", "test")
                .put("retries", 3)
                .done()
            .sendAndAwait();

        assertThat(response.getEvent()).containsEntry("eventType", "CREATED");
    }

    private void updateTaskRetries()
    {
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
    }

    private void updatePayload(final long workflowInstanceKey, final long activityInstanceKey, byte[] payload)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicName(ClientApiRule.DEFAULT_TOPIC_NAME)
            .partitionId(ClientApiRule.DEFAULT_PARTITION_ID)
            .eventType(EventType.WORKFLOW_EVENT)
            .key(activityInstanceKey)
            .command()
                .put("eventType", WorkflowInstanceEventType.UPDATE_PAYLOAD)
                .put("workflowInstanceKey", workflowInstanceKey)
                .put("payload", payload)
                .done()
            .sendAndAwait();

        assertThat(response.getEvent()).containsEntry("eventType", WorkflowInstanceEventType.PAYLOAD_UPDATED.name());
    }


    private void updatePayload(long workflowInstanceKey, SubscribedEvent activityInstanceEvent, String payload) throws IOException
    {
        updatePayload(workflowInstanceKey, activityInstanceEvent.key(), MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(payload)));
    }

    private void cancelWorkflowInstance(final long workflowInstanceKey)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicName(ClientApiRule.DEFAULT_TOPIC_NAME)
            .partitionId(ClientApiRule.DEFAULT_PARTITION_ID)
            .key(workflowInstanceKey)
            .eventTypeWorkflow()
            .command()
                .put("eventType", "CANCEL_WORKFLOW_INSTANCE")
                .done()
            .sendAndAwait();

        assertThat(response.getEvent()).containsEntry("eventType", "WORKFLOW_INSTANCE_CANCELED");
    }

}
