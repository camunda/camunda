package org.camunda.tngp.broker.protocol.clientapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.broker.incident.data.ErrorType;
import org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent;
import org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.util.HashMap;
import java.util.Map;

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
    private static final String NODE_BOOLEAN_KEY = "boolean";
    private static final String NODE_INTEGER_KEY = "integer";
    private static final String NODE_LONG_KEY = "long";
    private static final String NODE_DOUBLE_KEY = "double";
    private static final String NODE_ARRAY_KEY = "array";

    private static final String NODE_STRING_VALUE = "value";
    private static final boolean NODE_BOOLEAN_VALUE = false;
    private static final int NODE_INTEGER_VALUE = 1024;
    private static final long NODE_LONG_VALUE = Long.MAX_VALUE;
    private static final double NODE_DOUBLE_VALUE = 0.3;
    private static final String NODE_TEST_ATTR_VALUE = "test";
    private static final Integer[] NODE_ARRAY_VALUE = {0, 1, 2, 3};
    private static final Integer NODE_ARRAY_FIRST_IDX_VALUE = 0;

    private static final String NODE_STRING_PATH = "$.string";
    private static final String NODE_JSON_OBJECT_PATH = "$.jsonObject";
    private static final String NODE_ARRAY_PATH = "$.array";
    private static final String NODE_ARRAY_FIRST_IDX_PATH = "$.array[0]";
    private static final String NODE_ROOT_PATH = "$";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient testClient;

    protected final Map<String, Object> jsonPayload = new HashMap<>();
    protected final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
    private byte[] msgPackBytes;

    @Before
    public void init() throws Throwable
    {
        testClient = apiRule.topic();

        jsonPayload.put(NODE_STRING_KEY, NODE_STRING_VALUE);
        jsonPayload.put(NODE_BOOLEAN_KEY, NODE_BOOLEAN_VALUE);
        jsonPayload.put(NODE_INTEGER_KEY, NODE_INTEGER_VALUE);
        jsonPayload.put(NODE_LONG_KEY, NODE_LONG_VALUE);
        jsonPayload.put(NODE_DOUBLE_KEY, NODE_DOUBLE_VALUE);
        jsonPayload.put(NODE_ARRAY_KEY, NODE_ARRAY_VALUE);

        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put(NODE_TEST_ATTR_KEY, NODE_TEST_ATTR_VALUE);
        jsonPayload.put(NODE_JSON_OBJECT_KEY, jsonObject);
        msgPackBytes = objectMapper.writeValueAsBytes(jsonPayload);
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
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5)
            .containsEntry(PROP_TASK_PAYLOAD, msgPackBytes);
    }

    @Test
    public void shouldCreateNewObjectViaInputMapping() throws Throwable
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
            .done());

        // when
        testClient.createWorkflowInstance("process", msgPackBytes);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        final JsonNode jsonNode = objectMapper.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode newFooNode = jsonNode.get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldCreateNewDeepObjectViaInputMapping() throws Throwable
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
            .input(NODE_STRING_PATH, "$.newFoo.newDepth.string")
            .done());

        // when
        testClient.createWorkflowInstance("process", msgPackBytes);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        final JsonNode jsonNode = objectMapper.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode newFooNode = jsonNode.get("newFoo").get("newDepth").get(NODE_STRING_KEY);
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldCreateNewJSONObjectViaInputMapping() throws Throwable
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
            .input(NODE_JSON_OBJECT_PATH, "$.newObj")
            .done());

        // when
        testClient.createWorkflowInstance("process", msgPackBytes);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        final JsonNode jsonNode = objectMapper.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode newObjNode = jsonNode.get("newObj");
        assertThat(newObjNode.isObject()).isTrue();
        assertThat(newObjNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldCreateNewJsonArrayViaInputMapping() throws Throwable
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
            .input(NODE_ARRAY_PATH, "$.newArray")
            .done());

        // when
        testClient.createWorkflowInstance("process", msgPackBytes);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        final JsonNode jsonNode = objectMapper.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode newArrayNode = jsonNode.get("newArray");
        assertThatNodeContainsStartingArray(newArrayNode);
    }

    @Test
    public void shouldCreateObjectFromJSONArrayValueViaInputMapping() throws Throwable
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
                .input(NODE_ARRAY_FIRST_IDX_PATH, "$.firstIdxValue")
            .done());

        // when
        testClient.createWorkflowInstance("process", msgPackBytes);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        final JsonNode jsonNode = objectMapper.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode newArrayNode = jsonNode.get("firstIdxValue");
        assertThat(newArrayNode.isInt()).isTrue();
        assertThat(newArrayNode.intValue()).isEqualTo(0);
    }

    @Test
    public void shouldExtractValueFromObjectFromJsonArrayViaInputMapping() throws Throwable
    {
        // given
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put(NODE_TEST_ATTR_KEY, NODE_TEST_ATTR_VALUE);
        final Object[] array = {jsonObject};

        final Map<String, Object> rootObj = new HashMap<>();
        rootObj.put(NODE_ARRAY_KEY, array);
        final byte[] bytes = objectMapper.writeValueAsBytes(rootObj);

        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
                .input(NODE_ARRAY_FIRST_IDX_PATH + "." + NODE_TEST_ATTR_KEY, "$.testValue")
            .done());

        // when
        testClient.createWorkflowInstance("process", bytes);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        final JsonNode jsonNode = objectMapper.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));

        final JsonNode testValueNode = jsonNode.get("testValue");
        assertThat(testValueNode.isTextual()).isTrue();
        assertThat(testValueNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
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
        testClient.createWorkflowInstance("process", msgPackBytes);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        final JsonNode jsonNode = objectMapper.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode newFooNode = jsonNode.get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode newObjNode = jsonNode.get("newObj");
        assertThat(newObjNode.isObject()).isTrue();
        assertThat(newObjNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldCreateTwoNewObjectsInSameDepthViaInputMapping() throws Throwable
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
            .input(NODE_STRING_PATH, "$.newDepth.newFoo")
            .input(NODE_JSON_OBJECT_PATH, "$.newDepth.newObj")
            .done());

        // when
        testClient.createWorkflowInstance("process", msgPackBytes);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        final JsonNode jsonNode = objectMapper.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode newFooNode = jsonNode.get("newDepth").get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode newObjNode = jsonNode.get("newDepth").get("newObj");
        assertThat(newObjNode.isObject()).isTrue();
        assertThat(newObjNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldOverwriteObjectIfInputMappingMapsPathTwice() throws Throwable
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
            .input(NODE_STRING_PATH, "$.newObj")
            .input(NODE_JSON_OBJECT_PATH, "$.newObj")
            .done());

        // when
        testClient.createWorkflowInstance("process", msgPackBytes);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        final JsonNode jsonNode = objectMapper.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

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
    public void shouldUseDefaultInputMappingIfNullIsAsMappingSpecified() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service", null, null));

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5)
            .containsEntry(PROP_TASK_PAYLOAD, msgPackBytes);
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
        testClient.createWorkflowInstance("process", msgPackBytes);
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query '$.notExisting'.");
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
        testClient.createWorkflowInstance("process", msgPackBytes);
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query '$.notExisting'.");
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
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);

        // then
        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.key()).isEqualTo(activityActivatedEvent.key());
        assertThat(activityCompletedEvent.event())
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, msgPackBytes);
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
//        testClient.createWorkflowInstance("process", objectMapper.writeValueAsBytes(jsonObject));
//        final Long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);
//
//        // when
//        testClient.completeAllTaskOfType("external", new byte[][]{ objectMapper.writeValueAsBytes(jsonObject), msgPackBytes});
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
//        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
//        assertThat(jsonNode).isNotNull().isNotEmpty();
//        assertThat(activityCompletedEvent.longKey()).isEqualTo(activityActivatedEvent.longKey());
//        assertThat(activityCompletedEvent.event())
//            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, msgPackBytes);
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

        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);

        // then
        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.key()).isEqualTo(activityActivatedEvent.key());
        assertThat(activityCompletedEvent.event())
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, msgPackBytes);
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

        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external");

        // then
        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.key()).isEqualTo(activityActivatedEvent.key());
        assertThat(activityCompletedEvent.event())
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, EMTPY_OBJECT);
    }

    @Test
    public void shouldUseOutputMappingToAddValueToWorkflowPayload() throws Throwable
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
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new one
        final JsonNode newFooNode = jsonNode.get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldUseOutputMappingToAddJsonObjectToWorkflowPayload() throws Throwable
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
                .output(NODE_JSON_OBJECT_PATH, "$.newObj")
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new one
        final JsonNode objNode = jsonNode.get("newObj");
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldUseOutputMappingToAddJsonArrayToWorkflowPayload() throws Throwable
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
            .output(NODE_ARRAY_PATH, "$.newArray")
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThatNodeContainsTheStartingPayload(jsonNode);


        // and the new one
        final JsonNode newArrayNode = jsonNode.get("newArray");
        assertThatNodeContainsStartingArray(newArrayNode);
    }

    @Test
    public void shouldUseOutputMappingToExtractObjectFromJsonArrayToWorkflowPayload() throws Throwable
    {
        // given
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put(NODE_TEST_ATTR_KEY, NODE_TEST_ATTR_VALUE);
        final Object[] array = {jsonObject};

        final Map<String, Object> rootObj = new HashMap<>();
        rootObj.put(NODE_ARRAY_KEY, array);
        final byte[] bytes = objectMapper.writeValueAsBytes(rootObj);

        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
                .output(NODE_ARRAY_FIRST_IDX_PATH + "." + NODE_TEST_ATTR_KEY, "$.testValue")
            .done());
        testClient.createWorkflowInstance("process", bytes);

        // when
        testClient.completeTaskOfType("external", bytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));

        final JsonNode arrayNode = jsonNode.get(NODE_ARRAY_KEY);
        assertThat(arrayNode.isArray()).isTrue();

        final JsonNode arrayObjectNode = arrayNode.get(0);
        assertThat(arrayObjectNode.isObject()).isTrue();
        assertThat(arrayObjectNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        // and the new one
        final JsonNode testValueNode = jsonNode.get("testValue");
        assertThat(testValueNode.isTextual()).isTrue();
        assertThat(testValueNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldUseOutputMappingToAddJsonArrayValueToWorkflowPayload() throws Throwable
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
            .output(NODE_ARRAY_FIRST_IDX_PATH, "$.newValue")
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new one
        final JsonNode newValueNode = jsonNode.get("newValue");
        assertThat(newValueNode.isInt()).isTrue();
        assertThat(newValueNode.intValue()).isEqualTo(0);
    }

    @Test
    public void shouldUseOutputMappingToReplaceJsonArrayValueAtIndexToWorkflowPayload() throws Throwable
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
            .output(NODE_STRING_PATH, NODE_ARRAY_FIRST_IDX_PATH)
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();
        final JsonNode fooNode = jsonNode.get(NODE_STRING_KEY);
        assertThat(fooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode booleanNode = jsonNode.get(NODE_BOOLEAN_KEY);
        assertThat(booleanNode.booleanValue()).isEqualTo(NODE_BOOLEAN_VALUE);

        final JsonNode integerNode = jsonNode.get(NODE_INTEGER_KEY);
        assertThat(integerNode.intValue()).isEqualTo(NODE_INTEGER_VALUE);

        final JsonNode longNode = jsonNode.get(NODE_LONG_KEY);
        assertThat(longNode.longValue()).isEqualTo(NODE_LONG_VALUE);

        final JsonNode doubleNode = jsonNode.get(NODE_DOUBLE_KEY);
        assertThat(doubleNode.doubleValue()).isEqualTo(NODE_DOUBLE_VALUE);

        final JsonNode objNode = jsonNode.get(NODE_JSON_OBJECT_KEY);
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        // and overriden value on index 0
        final JsonNode arrayNode = jsonNode.get(NODE_ARRAY_KEY);
        assertThat(arrayNode.isArray()).isTrue();
        assertThat(arrayNode.get(0).textValue()).isEqualTo(NODE_STRING_VALUE);
        assertThat(arrayNode.get(1).intValue()).isEqualTo(1);
        assertThat(arrayNode.get(2).intValue()).isEqualTo(2);
        assertThat(arrayNode.get(3).intValue()).isEqualTo(3);
    }

    @Test
    public void shouldUseOutputMappingToReplaceObjectFromJsonArrayToWorkflowPayload() throws Throwable
    {
        // given
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put(NODE_TEST_ATTR_KEY, NODE_TEST_ATTR_VALUE);
        final Object[] array = {jsonObject};

        final Map<String, Object> rootObj = new HashMap<>();
        rootObj.put(NODE_ARRAY_KEY, array);
        rootObj.put(NODE_STRING_KEY, NODE_STRING_VALUE);
        final byte[] bytes = objectMapper.writeValueAsBytes(rootObj);

        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
            .output(NODE_STRING_PATH, NODE_ARRAY_FIRST_IDX_PATH + "." + NODE_TEST_ATTR_KEY)
            .done());
        testClient.createWorkflowInstance("process", bytes);

        // when
        testClient.completeTaskOfType("external", bytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));

        final JsonNode testValueNode = jsonNode.get(NODE_STRING_KEY);
        assertThat(testValueNode.isTextual()).isTrue();
        assertThat(testValueNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        // and the new one
        final JsonNode arrayNode = jsonNode.get(NODE_ARRAY_KEY);
        assertThat(arrayNode.isArray()).isTrue();

        final JsonNode arrayObjectNode = arrayNode.get(0);
        assertThat(arrayObjectNode.isObject()).isTrue();
        assertThat(arrayObjectNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_STRING_VALUE);
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
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new ones
        final JsonNode newFooNode = jsonNode.get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode objNode = jsonNode.get("newObj");
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldUseOutputMappingToMapReplaceObjectWithValueInWorkflowPayload() throws Throwable
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
            .output(NODE_STRING_PATH, NODE_JSON_OBJECT_PATH)
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode fooNode = jsonNode.get(NODE_STRING_KEY);
        assertThat(fooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        // and the replaced value
        final JsonNode objNode = jsonNode.get(NODE_JSON_OBJECT_KEY);
        assertThat(objNode.textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldUseOutputMappingToMapReplaceValueWithObjectInWorkflowPayload() throws Throwable
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
            .output(NODE_JSON_OBJECT_PATH, NODE_STRING_PATH)
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode fooNode = jsonNode.get(NODE_STRING_KEY);
        assertThat(fooNode.isObject()).isTrue();
        assertThat(fooNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        // and the replaced object
        final JsonNode objNode = jsonNode.get(NODE_JSON_OBJECT_KEY);
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    // TODO fails for payload which contains non string objects
    @Ignore
    public void shouldUseOutputMappingToReplaceObjectWithComplexObjectInWorkflowPayload() throws Throwable
    {
        // given
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put(NODE_TEST_ATTR_KEY, NODE_TEST_ATTR_VALUE);
        jsonObject.put("testObj", jsonPayload);
        jsonObject.put("a", jsonPayload);
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
            .output(NODE_ROOT_PATH, NODE_JSON_OBJECT_PATH)
            .done());

        testClient.createWorkflowInstance("process", msgPackBytes);

        // to check if the right payload is send
//        byte[] bytes = objectMapper.writeValueAsBytes(jsonObject);
//
//        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
//        MsgPackReader reader = new MsgPackReader();
//        reader.wrap(buffer, 0, buffer.capacity());
//        while (reader.hasNext())
//        {
//            MsgPackToken msgPackToken = reader.readToken();
//            MsgPackType type = msgPackToken.getType();
//            if (type == MsgPackType.STRING)
//            {
//                System.out.println(msgPackToken.getValueBuffer().getStringWithoutLengthUtf8(0, msgPackToken.getValueBuffer().capacity()));
//            }
//            else if (type == MsgPackType.INTEGER) {
//                System.out.println(msgPackToken.getIntegerValue());
//            }
//        }


        // when
        testClient.completeTaskOfType("external", objectMapper.writeValueAsBytes(jsonObject));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode stringNode = jsonNode.get(NODE_STRING_KEY);
        assertThat(stringNode.isTextual()).isTrue();
        assertThat(stringNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        // and the replaced object
        final JsonNode objNode = jsonNode.get(NODE_JSON_OBJECT_KEY);
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        final JsonNode aNode = objNode.get("a");
        assertThatNodeContainsTheStartingPayload(aNode);

        final JsonNode testObjNode = objNode.get("testObj");
        assertThatNodeContainsTheStartingPayload(testObjNode);
    }

    // TODO json object with the same key and name are not correctly identified
    @Ignore
    public void shouldUseOutputMappingToReplaceObjectInComplexObjectWithValueInWorkflowPayload() throws Throwable
    {
        // given
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("value", NODE_TEST_ATTR_VALUE);
        jsonObject.put("complexObject1", jsonPayload);
        jsonObject.put("complexObject2", jsonPayload);
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service")
            .output("$.value", "$.complexObject2.jsonObject")
            .done());

        testClient.createWorkflowInstance("process", objectMapper.writeValueAsBytes(jsonObject));

        // when
        testClient.completeTaskOfType("external", objectMapper.writeValueAsBytes(jsonObject));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode valueNode = jsonNode.get("value");
        assertThat(valueNode.isTextual()).isTrue();
        assertThat(valueNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        final JsonNode complexObject1Node = jsonNode.get("complexObject1");
        assertThatNodeContainsTheStartingPayload(complexObject1Node);

        // and the replaced object
        final JsonNode complexObject2Node = jsonNode.get("complexObject2");
        assertThat(complexObject2Node.isObject()).isTrue();

        final JsonNode fooNode = complexObject2Node.get(NODE_STRING_KEY);
        assertThat(fooNode.isTextual()).isTrue();
        assertThat(fooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode jsonObjNode = complexObject2Node.get(NODE_JSON_OBJECT_KEY);
        assertThat(jsonObjNode.isTextual()).isTrue();
        assertThat(jsonObjNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldUseOutputMappingToAddObjectInOtherObjectInWorkflowPayload() throws Throwable
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
            .output(NODE_STRING_PATH, "$.jsonObject.newFoo")
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new added value in the existing object
        assertThat(jsonNode.get(NODE_JSON_OBJECT_KEY).get("newFoo").textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldUseOutputMappingToMapHolePayloadToNewObjectInWorkflowPayload() throws Throwable
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
            .output(NODE_ROOT_PATH, "$.taskPayload")
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains old objects
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new added task payload
        final JsonNode taskPayloadNode = jsonNode.get("taskPayload");
        assertThatNodeContainsTheStartingPayload(taskPayloadNode);
    }

    @Test
    public void shouldUseOutputMappingToMapObjectToHoleWorkflowPayload() throws Throwable
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
            .output(NODE_JSON_OBJECT_PATH, NODE_ROOT_PATH)
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains jsonobject values
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode.isObject()).isTrue();
        assertThat(jsonNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldUseOutputMappingToMapValueToHoleWorkflowPayload() throws Throwable
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
            .output(NODE_STRING_PATH, NODE_ROOT_PATH)
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then payload contains jsonobject values
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode.isTextual()).isTrue();
        assertThat(jsonNode.textValue()).isEqualTo(NODE_STRING_VALUE);
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
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent incidentEvent = testClient.receiveSingleEvent(incidentEvents("CREATE"));

        // then incident is created
        assertThat(incidentEvent.key()).isGreaterThan(0);
        assertThat(incidentEvent.event())
            .containsEntry("errorType", ErrorType.IO_MAPPING_ERROR.name())
            .containsEntry("errorMessage", "No data found for query '$.notExisting'.");
    }

    @Test
    public void shouldOverwriteObjectIfOutputMappingMapsPathTwice() throws Throwable
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
            .output(NODE_STRING_PATH, "$.newObj")
            .output(NODE_JSON_OBJECT_PATH, "$.newObj")
            .done());
        testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // then old payload exist
        final JsonNode jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // new obj has value of json object
        final JsonNode newObjNode = jsonNode.get("newObj");
        assertThat(newObjNode.isObject()).isTrue();
        assertThat(newObjNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
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
        testClient.createWorkflowInstance("process", msgPackBytes);
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        // then payload is expected as
        JsonNode jsonNode = objectMapper.readTree((byte[]) event.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode testAttrNode = jsonNode.get("testAttr");
        assertThat(testAttrNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        // when
        testClient.completeTaskOfType("external", objectMapper.writeValueAsBytes(jsonObject));

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        jsonNode = objectMapper.readTree((byte[]) activityCompletedEvent.event().get(PROP_TASK_PAYLOAD));
        assertThat(jsonNode).isNotNull();
        assertThat(jsonNode.isTextual()).isTrue();
        assertThat(jsonNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    private void assertThatNodeContainsTheStartingPayload(JsonNode jsonNode)
    {
        assertThat(jsonNode).isNotNull().isNotEmpty();
        final JsonNode fooNode = jsonNode.get(NODE_STRING_KEY);
        assertThat(fooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode booleanNode = jsonNode.get(NODE_BOOLEAN_KEY);
        assertThat(booleanNode.booleanValue()).isEqualTo(NODE_BOOLEAN_VALUE);

        final JsonNode integerNode = jsonNode.get(NODE_INTEGER_KEY);
        assertThat(integerNode.intValue()).isEqualTo(NODE_INTEGER_VALUE);

        final JsonNode longNode = jsonNode.get(NODE_LONG_KEY);
        assertThat(longNode.longValue()).isEqualTo(NODE_LONG_VALUE);

        final JsonNode doubleNode = jsonNode.get(NODE_DOUBLE_KEY);
        assertThat(doubleNode.doubleValue()).isEqualTo(NODE_DOUBLE_VALUE);

        final JsonNode objNode = jsonNode.get(NODE_JSON_OBJECT_KEY);
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        final JsonNode arrayNode = jsonNode.get(NODE_ARRAY_KEY);
        assertThatNodeContainsStartingArray(arrayNode);
    }

    private static void assertThatNodeContainsStartingArray(JsonNode newArrayNode)
    {
        assertThat(newArrayNode.isArray()).isTrue();
        assertThat(newArrayNode.get(0).intValue()).isEqualTo(0);
        assertThat(newArrayNode.get(1).intValue()).isEqualTo(1);
        assertThat(newArrayNode.get(2).intValue()).isEqualTo(2);
        assertThat(newArrayNode.get(3).intValue()).isEqualTo(3);
    }
}
