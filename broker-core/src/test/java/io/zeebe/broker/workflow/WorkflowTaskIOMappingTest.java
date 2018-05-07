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
package io.zeebe.broker.workflow;

import static io.zeebe.broker.test.MsgPackUtil.JSON_DOCUMENT;
import static io.zeebe.broker.test.MsgPackUtil.JSON_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_PAYLOAD;
import static io.zeebe.msgpack.spec.MsgPackHelper.NIL;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.TaskIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;

/**
 * Represents a test class to test the input and output mappings for
 * tasks inside a workflow.
 */
public class WorkflowTaskIOMappingTest
{
    private static final String PROP_TASK_PAYLOAD = "payload";
    private static final String PROP_ERRO_MSG = "errorMessage";

    private static final String NODE_STRING_KEY = "string";

    private static final String NODE_STRING_PATH = "$.string";
    private static final String NODE_JSON_OBJECT_PATH = "$.jsonObject";
    private static final String NODE_ROOT_PATH = "$";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient testClient;


    @Before
    public void init()
    {
        testClient = apiRule.topic();
    }

    @Test
    public void shouldNotDeployIfInputMappingIsNotValid() throws Throwable
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external")
                             .input("$.*", NODE_ROOT_PATH))
                .endEvent()
                .done();

        // when
        final ExecuteCommandResponse response = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, definition);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.getValue().get(PROP_ERRO_MSG).toString())
            .contains("Source mapping: JSON path '$.*' contains prohibited expression");
    }

    @Test
    public void shouldNotDeployIfInputMappingMapsRootAndOtherObject() throws Throwable
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external")
                             .input(NODE_STRING_PATH, NODE_ROOT_PATH)
                             .input(NODE_JSON_OBJECT_PATH, NODE_JSON_OBJECT_PATH))
                .endEvent()
                .done();

        // when
        final ExecuteCommandResponse response = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, definition);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.getValue().get(PROP_ERRO_MSG).toString())
            .contains("Target mapping: root mapping is not allowed because it would override other mapping.");
    }

    @Test
    public void shouldUseDefaultInputMappingIfNoMappingIsSpecified() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external"))
                .endEvent()
                .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // then
        final SubscribedRecord event = testClient.receiveCommands()
            .ofTypeTask()
            .withIntent(TaskIntent.CREATE)
            .getFirst();

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        final byte[] result = (byte[]) event.value().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));
    }

    @Test
    public void shouldCreateTwoNewObjectsViaInputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external")
                             .input(NODE_STRING_PATH, "$.newFoo")
                             .input(NODE_JSON_OBJECT_PATH, "$.newObj"))
                .endEvent()
                .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedRecord event = receiveFirstTaskCommand(TaskIntent.CREATE);

        // then payload is expected as
        final byte[] result = (byte[]) event.value().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree("{'newFoo':'value', 'newObj':{'testAttr':'test'}}"));
    }

    @Test
    public void shouldUseNILIfCreatedWithNoPayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external"))
                .endEvent()
                .done());

        // when
        testClient.createWorkflowInstance("process");
        final SubscribedRecord event = receiveFirstTaskCommand(TaskIntent.CREATE);

        // then
        assertThat(event.value()).containsEntry(WorkflowInstanceRecord.PROP_WORKFLOW_PAYLOAD, NIL);
    }


    @Test
    public void shouldCreateIncidentForNoMatchOnInputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external")
                             .input("$.notExisting", NODE_ROOT_PATH))
                .endEvent()
                .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedRecord incidentEvent = receiveFirstIncidentCommand(IncidentIntent.CREATE);

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.notExisting.");
    }

    @Test
    public void shouldCreateIncidentForNonMatchingAndMatchingValueOnInputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external")
                             .input("$.notExisting", "$.nullVal")
                             .input(NODE_STRING_PATH, "$.existing"))

                .endEvent()
                .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedRecord incidentEvent = receiveFirstIncidentCommand(IncidentIntent.CREATE);

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.notExisting.");
    }

    @Test
    public void shouldNotDeployIfOutputMappingIsNotValid() throws Throwable
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external")
                             .output(NODE_STRING_KEY, ""))
                .endEvent()
                .done();

        final ExecuteCommandResponse response = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, definition);

        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    }

    @Test
    public void shouldUseDefaultOutputMappingIfNoMappingIsSpecified() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external"))
                .endEvent()
                .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_PAYLOAD);

        // then
        final SubscribedRecord activityCompletedEvent = receiveFirstWorkflowInstanceEvent(
                WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));
    }

    @Test
    public void shouldNotSeePayloadOfWorkflowInstanceBefore() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external"))
                          .endEvent()
                          .done());

        final long firstWFInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final long secondWFInstanceKey = testClient.createWorkflowInstance("process");

        // when
        testClient.completeTaskOfWorkflowInstance("external", firstWFInstanceKey, MSGPACK_PAYLOAD);
        testClient.completeTaskOfWorkflowInstance("external", secondWFInstanceKey, MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}")));

        // then first event payload is expected as
        final SubscribedRecord firstWFActivityCompletedEvent =
                receiveFirstWorkflowInstanceEvent(firstWFInstanceKey, WorkflowInstanceIntent.ACTIVITY_COMPLETED);
        byte[] payload = (byte[]) firstWFActivityCompletedEvent.value().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload)).isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));

        // and second event payload is expected as
        final SubscribedRecord secondWFActivityCompletedEvent =
                receiveFirstWorkflowInstanceEvent(secondWFInstanceKey, WorkflowInstanceIntent.ACTIVITY_COMPLETED);
        payload = (byte[]) secondWFActivityCompletedEvent.value().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload)).isEqualTo(JSON_MAPPER.readTree("{'foo':'bar'}"));
    }

    @Test
    public void shouldNotSeePayloadOfWorkflowInstanceBeforeOnOutputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .output("$", "$.taskPayload"))
                          .endEvent()
                          .done());

        final long firstWFInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final long secondWFInstanceKey = testClient.createWorkflowInstance("process",
                                                                           MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'otherPayload':'value'}")));

        // when
        testClient.completeTaskOfWorkflowInstance("external", firstWFInstanceKey, MSGPACK_PAYLOAD);
        testClient.completeTaskOfWorkflowInstance("external", secondWFInstanceKey, MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}")));

        // then first event payload is expected as
        final SubscribedRecord firstWFActivityCompletedEvent =
                receiveFirstWorkflowInstanceEvent(firstWFInstanceKey, WorkflowInstanceIntent.ACTIVITY_COMPLETED);
        byte[] payload = (byte[]) firstWFActivityCompletedEvent.value().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload))
            .isEqualTo(JSON_MAPPER.readTree("{'string':'value', 'jsonObject':{'testAttr':'test'},'taskPayload':{'string':'value', 'jsonObject':{'testAttr':'test'}}}"));

        // and second event payload is expected as
        final SubscribedRecord secondWFActivityCompletedEvent =
                receiveFirstWorkflowInstanceEvent(secondWFInstanceKey, WorkflowInstanceIntent.ACTIVITY_COMPLETED);
        payload = (byte[]) secondWFActivityCompletedEvent.value().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload))
            .isEqualTo(JSON_MAPPER.readTree("{'otherPayload':'value','taskPayload':{'foo':'bar'}}"));
    }

    @Test
    public void shouldUseDefaultOutputMappingIfNullSpecified() throws Throwable
    {
        // given
        final Map<String, String> inputMapping = new HashMap<>();
        inputMapping.put(NODE_ROOT_PATH, NODE_ROOT_PATH);
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                                       .input(NODE_ROOT_PATH, NODE_ROOT_PATH))
                          .endEvent()
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_PAYLOAD);

        // then
        final SubscribedRecord activityCompletedEvent =
                receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));
    }

    @Test
    public void shouldUseWFPayloadIfCompleteWithNoPayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external"))
                          .endEvent()
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external");

        // then
        final SubscribedRecord activityCompletedEvent =
                receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        assertThat(activityCompletedEvent.value())
            .containsEntry(WorkflowInstanceRecord.PROP_WORKFLOW_PAYLOAD, MSGPACK_PAYLOAD);
    }

    @Test
    public void shouldUseOutputMappingToAddObjectsToWorkflowPayload() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                              .output(NODE_STRING_PATH, "$.newFoo")
                              .output(NODE_JSON_OBJECT_PATH, "$.newObj"))
                          .endEvent()
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_PAYLOAD);
        final SubscribedRecord activityCompletedEvent =
                receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        // then payload contains old objects
        final byte[] result = (byte[]) activityCompletedEvent.value().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(
                "{'newFoo':'value', 'newObj':{'testAttr':'test'}," +
                       " 'string':'value', 'jsonObject':{'testAttr':'test'}}"));
    }

    @Test
    public void shouldCreateIncidentForNotMatchingOnOutputMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                              .output("$.notExisting", "$.notExist"))
                          .endEvent()
                          .done());

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_PAYLOAD);
        receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED);

        final SubscribedRecord incidentEvent = receiveFirstIncidentCommand(IncidentIntent.CREATE);

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.value())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.notExisting.");
    }

    @Test
    public void shouldNotMapPayloadToWorkflowIfOutMappingMapsRootAndOtherPath() throws Throwable
    {
        // given
        final WorkflowDefinition definition = Bpmn.createExecutableWorkflow("process")
                .startEvent()
                .serviceTask("service", t -> t.taskType("external")
                    .output(NODE_STRING_PATH, NODE_ROOT_PATH)
                    .output(NODE_JSON_OBJECT_PATH, NODE_JSON_OBJECT_PATH))
                .endEvent()
                .done();

        // when
        final ExecuteCommandResponse response = apiRule.topic().deployWithResponse(ClientApiRule.DEFAULT_TOPIC_NAME, definition);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.getValue().get(PROP_ERRO_MSG).toString())
            .contains("Target mapping: root mapping is not allowed because it would override other mapping.");
    }

    @Test
    public void shouldUseInOutMapping() throws Throwable
    {
        // given
        testClient.deploy(Bpmn.createExecutableWorkflow("process")
                          .startEvent()
                          .serviceTask("service", t -> t.taskType("external")
                              .input(NODE_JSON_OBJECT_PATH, NODE_ROOT_PATH)
                              .output("$.testAttr", "$.result"))
                          .endEvent()
                          .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedRecord event = receiveFirstTaskCommand(TaskIntent.CREATE);

        // then payload is expected as
        byte[] result = (byte[]) event.value().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(
                "{'testAttr':'test'}"));

        // when
        testClient.completeTaskOfType("external", MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'testAttr':123}")));

        // then
        final SubscribedRecord activityCompletedEvent =
                receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ACTIVITY_COMPLETED);

        result = (byte[]) activityCompletedEvent.value().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(
                "{'string':'value', 'jsonObject':{'testAttr':'test'}, 'result':123}"));
    }

    private SubscribedRecord receiveFirstWorkflowInstanceEvent(Intent intent)
    {
        return testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(intent)
            .getFirst();
    }

    private SubscribedRecord receiveFirstWorkflowInstanceEvent(long key, Intent intent)
    {
        return testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(intent)
            .filter(r -> (Long) r.value().get("workflowInstanceKey") == key)
            .findFirst()
            .get();
    }

    private SubscribedRecord receiveFirstTaskCommand(Intent intent)
    {
        return testClient.receiveCommands()
            .ofTypeTask()
            .withIntent(intent)
            .getFirst();
    }


    private SubscribedRecord receiveFirstIncidentCommand(Intent intent)
    {
        return testClient.receiveCommands()
            .ofTypeIncident()
            .withIntent(intent)
            .getFirst();
    }
}
