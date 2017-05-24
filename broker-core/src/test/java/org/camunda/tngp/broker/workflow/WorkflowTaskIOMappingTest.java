package org.camunda.tngp.broker.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.broker.workflow.graph.transformer.validator.IOMappingRule.ERROR_MSG_PROHIBITED_EXPRESSION;
import static org.camunda.tngp.broker.workflow.graph.transformer.validator.IOMappingRule.ERROR_MSG_REDUNDANT_MAPPING;
import static org.camunda.tngp.broker.workflow.graph.transformer.validator.ValidationCodes.PROHIBITED_JSON_PATH_EXPRESSION;
import static org.camunda.tngp.broker.workflow.graph.transformer.validator.ValidationCodes.REDUNDANT_MAPPING;
import static org.camunda.tngp.msgpack.spec.MsgPackHelper.EMTPY_OBJECT;
import static org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_PARTITION_ID;
import static org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.*;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.broker.incident.data.ErrorType;
import org.camunda.tngp.broker.test.EmbeddedBrokerRule;
import org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent;
import org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.msgpack.jackson.dataformat.MessagePackFactory;

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

    private static final String NODE_JSON_OBJECT_KEY = "jsonObject";
    private static final String NODE_TEST_ATTR_KEY = "testAttr";
    private static final String NODE_STRING_KEY = "string";

    private static final String NODE_STRING_VALUE = "value";
    private static final String NODE_TEST_ATTR_VALUE = "test";

    private static final String NODE_STRING_PATH = "$.string";
    private static final String NODE_JSON_OBJECT_PATH = "$.jsonObject";
    private static final String NODE_ROOT_PATH = "$";


    protected static final Map<String, Object> JSON_PAYLOAD = new HashMap<>();
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new MessagePackFactory());
    private static final byte[] MSG_PACK_BYTES;
    static
    {

        JSON_PAYLOAD.put(NODE_STRING_KEY, NODE_STRING_VALUE);

        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put(NODE_TEST_ATTR_KEY, NODE_TEST_ATTR_VALUE);
        JSON_PAYLOAD.put(NODE_JSON_OBJECT_KEY, jsonObject);
        byte[] bytes = null;
        try
        {
            bytes = OBJECT_MAPPER.writeValueAsBytes(JSON_PAYLOAD);
        }
        catch (JsonProcessingException e)
        {
            e.printStackTrace();
        }
        finally
        {
            MSG_PACK_BYTES = bytes;
        }
    }

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

        final TngpExtensions.TngpModelInstance modelInstance = wrap(
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
        final TngpExtensions.TngpModelInstance modelInstance = wrap(
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
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSG_PACK_BYTES);

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5)
            .containsEntry(PROP_TASK_PAYLOAD, MSG_PACK_BYTES);
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
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", MSG_PACK_BYTES);

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5)
            .containsEntry(PROP_TASK_PAYLOAD, MSG_PACK_BYTES);
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
        testClient.createWorkflowInstance("process", MSG_PACK_BYTES);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode newFooNode = jsonNode.get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode newObjNode = jsonNode.get("newObj");
        assertThat(newObjNode.isObject()).isTrue();
        assertThat(newObjNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldUseEmptyMapIfCreatedWithNoPayload() throws Throwable
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
        assertThat(event.event()).containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, EMTPY_OBJECT);
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
        testClient.createWorkflowInstance("process", MSG_PACK_BYTES);
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
        testClient.createWorkflowInstance("process", MSG_PACK_BYTES);
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
        final TngpExtensions.TngpModelInstance modelInstance = wrap(
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
        testClient.createWorkflowInstance("process", MSG_PACK_BYTES);

        // when
        testClient.completeTaskOfType("external", MSG_PACK_BYTES);

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.event())
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, MSG_PACK_BYTES);
    }

// TODO two process instances should not see there payloads
// for that we have to create and complete WF instances
//    @Test
//    public void shouldNotSeePayloadOfWorkflowInstanceBefore() throws Throwable
//    {
//        // given
//        final Map<String, Object> jsonObject = new HashMap<>();
//        jsonObject.put("testAttr", "test");
//        jsonObject.put("testObj", jsonPayload);
//        jsonObject.put("a", jsonPayload);
//        testClient.deploy(wrap(
//            Bpmn.createExecutableProcess("process")
//                .startEvent()
//                .serviceTask("service")
//                .endEvent()
//                .done())
//            .taskDefinition("service", "external", 5));
//
//        testClient.createWorkflowInstance("process", OBJECT_MAPPER.writeValueAsBytes(jsonObject));
//        final Long workflowInstanceKey = testClient.createWorkflowInstance("process", MSG_PACK_BYTES);
//
//        // when
//        testClient.completeAllTaskOfType("external", new byte[][]{ OBJECT_MAPPER.writeValueAsBytes(jsonObject), MSG_PACK_BYTES});
//
//        // then
//        SubscribedEvent activityActivatedEvent =
//            testClient.receiveSingleEvent(
//                workflowInstanceEvents("ACTIVITY_ACTIVATED")
//                    .and(e -> e.event()
//                        .get(PROP_WORKFLOW_INSTANCE_KEY)
//                        .equals(workflowInstanceKey)));
//        SubscribedEvent activityCompletedEvent =
//            testClient.receiveSingleEvent(
//                workflowInstanceEvents("ACTIVITY_COMPLETED")
//                    .and(e -> e.event()
//                        .get(PROP_WORKFLOW_INSTANCE_KEY)
//                        .equals(workflowInstanceKey)));
//
//
//
//        final JsonNode jsonNode = OBJECT_MAPPER.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
//        assertThat(jsonNode).isNotNull().isNotEmpty();
//        assertThat(activityCompletedEvent.longKey()).isEqualTo(activityActivatedEvent.longKey());
//        assertThat(activityCompletedEvent.event())
//            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, MSG_PACK_BYTES);
//    }

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

        testClient.createWorkflowInstance("process", MSG_PACK_BYTES);

        // when
        testClient.completeTaskOfType("external", MSG_PACK_BYTES);

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.event())
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, MSG_PACK_BYTES);
    }

    @Test
    public void shouldUseEmptyMapIfCompleteWithNoPayload() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5));

        testClient.createWorkflowInstance("process", MSG_PACK_BYTES);

        // when
        testClient.completeTaskOfType("external");

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.event())
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, EMTPY_OBJECT);
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
        testClient.createWorkflowInstance("process", MSG_PACK_BYTES);

        // when
        testClient.completeTaskOfType("external", MSG_PACK_BYTES);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = OBJECT_MAPPER.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new ones
        final JsonNode newFooNode = jsonNode.get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode objNode = jsonNode.get("newObj");
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
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
        testClient.createWorkflowInstance("process", MSG_PACK_BYTES);

        // when
        testClient.completeTaskOfType("external", MSG_PACK_BYTES);
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
        final TngpExtensions.TngpModelInstance modelInstance = wrap(
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
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("testAttr", "test");
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
                .input(NODE_JSON_OBJECT_PATH, NODE_ROOT_PATH)
                .output("$.testAttr", NODE_ROOT_PATH)
             .done());

        // when
        testClient.createWorkflowInstance("process", MSG_PACK_BYTES);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        JsonNode jsonNode = OBJECT_MAPPER.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode testAttrNode = jsonNode.get("testAttr");
        assertThat(testAttrNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        // when
        testClient.completeTaskOfType("external", OBJECT_MAPPER.writeValueAsBytes(jsonObject));

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        jsonNode = OBJECT_MAPPER.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull();
        assertThat(jsonNode.isTextual()).isTrue();
        assertThat(jsonNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    private void assertThatNodeContainsTheStartingPayload(JsonNode jsonNode)
    {
        assertThat(jsonNode).isNotNull().isNotEmpty();
        final JsonNode fooNode = jsonNode.get(NODE_STRING_KEY);
        assertThat(fooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode objNode = jsonNode.get(NODE_JSON_OBJECT_KEY);
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

    }
}
