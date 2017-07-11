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

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.broker.util.msgpack.MsgPackUtil.*;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.broker.workflow.graph.transformer.validator.IOMappingRule.ERROR_MSG_PROHIBITED_EXPRESSION;
import static io.zeebe.broker.workflow.graph.transformer.validator.IOMappingRule.ERROR_MSG_REDUNDANT_MAPPING;
import static io.zeebe.broker.workflow.graph.transformer.validator.ValidationCodes.PROHIBITED_JSON_PATH_EXPRESSION;
import static io.zeebe.broker.workflow.graph.transformer.validator.ValidationCodes.REDUNDANT_MAPPING;
import static io.zeebe.msgpack.spec.MsgPackHelper.NIL;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_PARTITION_ID;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.*;

import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.model.bpmn.Bpmn;
import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedEvent;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * Represents a test class to test the input and output mappings for
 * tasks inside a workflow.
 */
public class WorkflowTaskIOMappingTest
{
    private static final String PROP_TASK_TYPE = "type";
    private static final String PROP_TASK_RETRIES = "retries";
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
        final HashMap<String, String> map = new HashMap<>();
        map.put("$.*", NODE_ROOT_PATH);

        final ZeebeExtensions.ZeebeModelInstance modelInstance = wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service", map, null);

        // when
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .eventType(EventType.DEPLOYMENT_EVENT)
            .command()
                .put(PROP_EVENT, "CREATE_DEPLOYMENT")
                .put(PROP_WORKFLOW_BPMN_XML, Bpmn.convertToString(modelInstance))
            .done()
            .sendAndAwait();

        // then
        assertThat(response.getEvent().get(PROP_EVENT)).isEqualTo("DEPLOYMENT_REJECTED");
        assertThat(response.getEvent().get(PROP_ERRO_MSG).toString())
            .contains(Integer.toString(PROHIBITED_JSON_PATH_EXPRESSION))
            .contains(ERROR_MSG_PROHIBITED_EXPRESSION);
    }

    @Test
    public void shouldNotDeployIfInputMappingMapsRootAndOtherObject() throws Throwable
    {
        // given
        final ZeebeExtensions.ZeebeModelInstance modelInstance = wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
                .input(NODE_STRING_PATH, NODE_ROOT_PATH)
                .input(NODE_JSON_OBJECT_PATH, NODE_JSON_OBJECT_PATH)
            .done();


        // when
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .eventType(EventType.DEPLOYMENT_EVENT)
            .command()
            .put(PROP_EVENT, "CREATE_DEPLOYMENT")
            .put(PROP_WORKFLOW_BPMN_XML, Bpmn.convertToString(modelInstance))
            .done()
            .sendAndAwait();

        // then
        assertThat(response.getEvent().get(PROP_EVENT)).isEqualTo("DEPLOYMENT_REJECTED");
        assertThat(response.getEvent().get(PROP_ERRO_MSG).toString())
            .contains(Integer.toString(REDUNDANT_MAPPING))
            .contains(ERROR_MSG_REDUNDANT_MAPPING);
    }

    @Test
    public void shouldUseDefaultInputMappingIfNoMappingIsSpecified() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5));

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5);
        final byte[] result = (byte[]) event.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));
    }

    @Test
    public void shouldUseDefaultInputMappingIfNullIsAsMappingSpecified() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done()).taskDefinition("service", "external", 5)
                        .ioMapping("service", null, null));

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5);

        final byte[] result = (byte[]) event.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));
    }

    @Test
    public void shouldCreateTwoNewObjectsViaInputMapping() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
                .input(NODE_STRING_PATH, "$.newFoo")
                .input(NODE_JSON_OBJECT_PATH, "$.newObj")
            .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        final byte[] result = (byte[]) event.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree("{'newFoo':'value', 'newObj':{'testAttr':'test'}}"));
    }

    @Test
    public void shouldUseNILIfCreatedWithNoPayload() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
            .done());

        // when
        testClient.createWorkflowInstance("process");
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then
        assertThat(event.event()).containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, NIL);
    }


    @Test
    public void shouldCreateIncidentForNoMatchOnInputMapping() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
                .input("$.notExisting", NODE_ROOT_PATH)
            .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.notExisting.");
    }

    @Test
    public void shouldCreateIncidentForNonMatchingAndMatchingValueOnInputMapping() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
                .input("$.notExisting", "$.nullVal")
                .input(NODE_STRING_PATH, "$.existing")
            .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.notExisting.");
    }

    @Test
    public void shouldNotDeployIfOutputMappingIsNotValid() throws Throwable
    {
        final Map<String, String> outMapping = new HashMap<>();
        outMapping.put(NODE_STRING_KEY, null);
        // given
        final ZeebeExtensions.ZeebeModelInstance modelInstance = wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service", null, outMapping);

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .eventType(EventType.DEPLOYMENT_EVENT)
            .command()
            .put(PROP_EVENT, "CREATE_DEPLOYMENT")
            .put(PROP_WORKFLOW_BPMN_XML, Bpmn.convertToString(modelInstance))
            .done()
            .sendAndAwait();

        assertThat(response.getEvent().get(PROP_EVENT)).isEqualTo("DEPLOYMENT_REJECTED");
    }

    @Test
    public void shouldUseDefaultOutputMappingIfNoMappingIsSpecified() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5));
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_PAYLOAD);

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        final byte[] result = (byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));
    }

    @Test
    public void shouldNotSeePayloadOfWorkflowInstanceBefore() throws Throwable
    {
        // given
        testClient.deploy(wrap(Bpmn.createExecutableProcess("process")
                                   .startEvent()
                                   .serviceTask("service")
                                   .endEvent()
                                   .done()).taskDefinition("service", "external", 5));

        final long firstWFInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final long secondWFInstanceKey = testClient.createWorkflowInstance("process");

        // when
        testClient.completeTaskOfWorkflowInstance("external", firstWFInstanceKey, MSGPACK_PAYLOAD);
        testClient.completeTaskOfWorkflowInstance("external", secondWFInstanceKey, MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}")));

        // then first event payload is expected as
        final SubscribedEvent firstWFActivityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED", firstWFInstanceKey));
        byte[] payload = (byte[]) firstWFActivityCompletedEvent.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload)).isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));

        // and second event payload is expected as
        final SubscribedEvent secondWFActivityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED", secondWFInstanceKey));
        payload = (byte[]) secondWFActivityCompletedEvent.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload)).isEqualTo(JSON_MAPPER.readTree("{'foo':'bar'}"));
    }

    @Test
    public void shouldNotSeePayloadOfWorkflowInstanceBeforeOnOutputMapping() throws Throwable
    {
        // given
        testClient.deploy(wrap(Bpmn.createExecutableProcess("process")
                                   .startEvent()
                                   .serviceTask("service")
                                   .endEvent()
                                   .done()).taskDefinition("service", "external", 5).ioMapping("service").output("$", "$.taskPayload").done());

        final long firstWFInstanceKey = testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final long secondWFInstanceKey = testClient.createWorkflowInstance("process",
                                                                           MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'otherPayload':'value'}")));

        // when
        testClient.completeTaskOfWorkflowInstance("external", firstWFInstanceKey, MSGPACK_PAYLOAD);
        testClient.completeTaskOfWorkflowInstance("external", secondWFInstanceKey, MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}")));

        // then first event payload is expected as
        final SubscribedEvent firstWFActivityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED", firstWFInstanceKey));
        byte[] payload = (byte[]) firstWFActivityCompletedEvent.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload))
            .isEqualTo(JSON_MAPPER.readTree("{'string':'value', 'jsonObject':{'testAttr':'test'},'taskPayload':{'string':'value', 'jsonObject':{'testAttr':'test'}}}"));

        // and second event payload is expected as
        final SubscribedEvent secondWFActivityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED", secondWFInstanceKey));
        payload = (byte[]) secondWFActivityCompletedEvent.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(payload))
            .isEqualTo(JSON_MAPPER.readTree("{'otherPayload':'value','taskPayload':{'foo':'bar'}}"));
    }

    @Test
    public void shouldUseDefaultOutputMappingIfNullSpecified() throws Throwable
    {
        // given
        final Map<String, String> inputMapping = new HashMap<>();
        inputMapping.put(NODE_ROOT_PATH, NODE_ROOT_PATH);
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service", inputMapping, null));

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_PAYLOAD);

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        final byte[] result = (byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(JSON_DOCUMENT));
    }

    @Test
    public void shouldUseWFPayloadIfCompleteWithNoPayload() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5));

        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external");

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.event())
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, MSGPACK_PAYLOAD);
    }

    @Test
    public void shouldUseOutputMappingToAddObjectsToWorkflowPayload() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
                .output(NODE_STRING_PATH, "$.newFoo")
                .output(NODE_JSON_OBJECT_PATH, "$.newObj")
            .done());
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_PAYLOAD);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final byte[] result = (byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(
                "{'newFoo':'value', 'newObj':{'testAttr':'test'}," +
                       " 'string':'value', 'jsonObject':{'testAttr':'test'}}"));
    }

    @Test
    public void shouldCreateIncidentForNotMatchingOnOutputMapping() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
                .output("$.notExisting", "$.notExist")
            .done());
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);

        // when
        testClient.completeTaskOfType("external", MSGPACK_PAYLOAD);
        testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query $.notExisting.");
    }

    @Test
    public void shouldNotMapPayloadToWorkflowIfOutMappingMapsRootAndOtherPath() throws Throwable
    {
        // given
        final ZeebeExtensions.ZeebeModelInstance modelInstance = wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
            .output(NODE_STRING_PATH, NODE_ROOT_PATH)
            .output(NODE_JSON_OBJECT_PATH, NODE_JSON_OBJECT_PATH)
            .done();


        // when
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .eventType(EventType.DEPLOYMENT_EVENT)
            .command()
            .put(PROP_EVENT, "CREATE_DEPLOYMENT")
            .put(PROP_WORKFLOW_BPMN_XML, Bpmn.convertToString(modelInstance))
            .done()
            .sendAndAwait();

        // then
        assertThat(response.getEvent().get(PROP_EVENT)).isEqualTo("DEPLOYMENT_REJECTED");
        assertThat(response.getEvent().get(PROP_ERRO_MSG).toString())
            .contains(Integer.toString(REDUNDANT_MAPPING))
            .contains(ERROR_MSG_REDUNDANT_MAPPING);
    }

    @Test
    public void shouldUseInOutMapping() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
                .input(NODE_JSON_OBJECT_PATH, NODE_ROOT_PATH)
                .output("$.testAttr", "$.result")
             .done());

        // when
        testClient.createWorkflowInstance("process", MSGPACK_PAYLOAD);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        byte[] result = (byte[]) event.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(
                "{'testAttr':'test'}"));

        // when
        testClient.completeTaskOfType("external", MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'testAttr':123}")));

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        result = (byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD);
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree(
                "{'testAttr':'test', 'result':123}"));
    }
}
