package org.camunda.tngp.broker.protocol.clientapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_ACTIVITY_ID;
import static org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_INSTANCE_KEY;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.msgpack.spec.MsgPackHelper.EMTPY_OBJECT;
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
        testClient = apiRule.topic(0);

        jsonPayload.put("foo", "bar");
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("testAttr", "test");
        jsonPayload.put("jsonObject", jsonObject);
        msgPackBytes = objectMapper.writeValueAsBytes(jsonPayload);
    }

    @Test
    public void shouldNotDeployIfInputMappingIsNotValid() throws Throwable
    {
        // given
        final TngpExtensions.TngpModelInstance modelInstance = wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service", "foo", null);

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicId(0)
            .eventType(EventType.DEPLOYMENT_EVENT)
            .command()
                .put(PROP_EVENT, "CREATE_DEPLOYMENT")
                .put(PROP_WORKFLOW_BPMN_XML, Bpmn.convertToString(modelInstance))
            .done()
            .sendAndAwait();

        assertThat(response.getEvent().get(PROP_EVENT)).isEqualTo("DEPLOYMENT_REJECTED");
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

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5)
            .containsEntry(PROP_TASK_PAYLOAD, msgPackBytes);
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

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5)
            .containsEntry(PROP_TASK_PAYLOAD, msgPackBytes);
    }

    @Test
    public void shouldUseInputMappingToMapPayloadToTask() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service", "$.foo", null));

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5)
            .containsEntry(PROP_TASK_PAYLOAD, objectMapper.writeValueAsBytes("bar"));
    }

    @Test
    public void shouldUseInputMappingToMapObjectToTask() throws Throwable
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
            .ioMapping("service", "$.jsonObject", null));

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5)
            .containsEntry(PROP_TASK_PAYLOAD, objectMapper.writeValueAsBytes(jsonObject));
    }

    @Test
    public void shouldNotMapPayloadIfJsonPathDoesNotMatch() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service", "$.notExisting", null));

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5)
            .containsEntry(PROP_TASK_PAYLOAD, EMTPY_OBJECT);
    }

    @Test
    public void shouldNotDeployIfOutputMappingIsNotValid() throws Throwable
    {
        // given
        final TngpExtensions.TngpModelInstance modelInstance = wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service", null, "foo");

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicId(0)
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
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);

        // then
        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.longKey()).isEqualTo(activityActivatedEvent.longKey());
        assertThat(activityCompletedEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "service")
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, msgPackBytes);
    }

    @Test
    public void shouldUseDefaultOutputMappingIfNullSpecified() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service", "$", null));

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);

        // then
        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.longKey()).isEqualTo(activityActivatedEvent.longKey());
        assertThat(activityCompletedEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "service")
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
            .taskDefinition("service", "external", 5)
            .ioMapping("service", "$", null));

        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external");

        // then
        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.longKey()).isEqualTo(activityActivatedEvent.longKey());
        assertThat(activityCompletedEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "service")
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, EMTPY_OBJECT);
    }

    @Test
    public void shouldUseOutputMappingToMapPayloadToWorkflow() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service", null, "$.foo"));
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);

        // then
        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.longKey()).isEqualTo(activityActivatedEvent.longKey());
        assertThat(activityCompletedEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "service")
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, objectMapper.writeValueAsBytes("bar"));
    }

    @Test
    public void shouldUseOutputMappingToMapObjectToWorkflow() throws Throwable
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
            .ioMapping("service", null, "$.jsonObject"));
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);

        // then
        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.longKey()).isEqualTo(activityActivatedEvent.longKey());
        assertThat(activityCompletedEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "service")
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, objectMapper.writeValueAsBytes(jsonObject));
    }

    @Test
    public void shouldNotMapPayloadToWorkflowIfJsonPathDoesNotMatch() throws Throwable
    {
        // given
        testClient.deploy(wrap(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("service")
                .endEvent()
                .done())
            .taskDefinition("service", "external", 5)
            .ioMapping("service", null, "$.notExisting"));
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // when
        testClient.completeTaskOfType("external", msgPackBytes);

        // then
        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.longKey()).isEqualTo(activityActivatedEvent.longKey());
        assertThat(activityCompletedEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "service")
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, EMTPY_OBJECT);
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
            .ioMapping("service", "$.jsonObject", "$.testAttr"));

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process", msgPackBytes);

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "external")
            .containsEntry(PROP_TASK_RETRIES, 5)
            .containsEntry(PROP_TASK_PAYLOAD, objectMapper.writeValueAsBytes(jsonObject));

        // when
        testClient.completeTaskOfType("external", objectMapper.writeValueAsBytes(jsonObject));

        // then
        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.longKey()).isEqualTo(activityActivatedEvent.longKey());
        assertThat(activityCompletedEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "service")
            .containsEntry(WorkflowInstanceEvent.PROP_WORKFLOW_PAYLOAD, objectMapper.writeValueAsBytes("test"));
    }
}
