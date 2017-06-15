package org.camunda.tngp.broker.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.createMsgPack;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.test.EmbeddedBrokerRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class UpdatePayloadTest
{

    private static final BpmnModelInstance WORKFLOW = wrap(
            Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task-1")
            .serviceTask("task-2")
            .endEvent()
            .done())
                .taskDefinition("task-1", "task-1", 5)
                .taskDefinition("task-2", "task-2", 5)
                .ioMapping("task-1")
                    .output("$.a", "$.a")
                    .done();

    protected static final ObjectMapper MSGPACK_MAPPER = new ObjectMapper(new MessagePackFactory());
    protected static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final byte[] MSG_PACK_BYTES;

    public static final String JSON_DOCUMENT = "{'a':'foo'}";

    static
    {
        JSON_MAPPER.configure(Feature.ALLOW_SINGLE_QUOTES, true);
        byte[] bytes = null;
        try
        {
            bytes = MSGPACK_MAPPER.writeValueAsBytes(
                JSON_MAPPER.readTree(JSON_DOCUMENT));
        }
        catch (Exception e)
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
    public void shouldUpdatePayload() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent activityInstanceEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey,
                                                              activityInstanceEvent.key(),
                                                              MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}")));

        // then
        assertThat(response.getEvent()).containsEntry("eventType", "PAYLOAD_UPDATED");

        final SubscribedEvent updatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("PAYLOAD_UPDATED"));

        assertThat(updatedEvent.key()).isEqualTo(activityInstanceEvent.key());
        assertThat(updatedEvent.event()).containsEntry("workflowInstanceKey", workflowInstanceKey);

        final byte[] payload = (byte[]) updatedEvent.event().get("payload");

        assertThat(MSGPACK_MAPPER.readTree(payload))
            .isEqualTo(JSON_MAPPER.readTree("{'foo':'bar'}"));
    }

    @Test
    public void shouldUpdatePayloadWhenActivityActivated() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent activityInstanceEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        // when
        updatePayload(workflowInstanceKey, activityInstanceEvent.key(),
                      MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'b':'wf'}")));

        testClient.receiveSingleEvent(workflowInstanceEvents("PAYLOAD_UPDATED"));

        testClient.completeTaskOfType("task-1", createMsgPack().put("a", "task").done());

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        final byte[] payload = (byte[]) activityCompletedEvent.event().get("payload");

        assertThat(MSGPACK_MAPPER.readTree(payload))
            .isEqualTo(JSON_MAPPER.readTree("{'a':'task', 'b':'wf'}"));
    }

    @Test
    public void shouldRejectUpdateForCompletedActivity() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeTaskOfType("task-1", MSG_PACK_BYTES);

        final SubscribedEvent activityInstanceEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey, activityInstanceEvent.key(), MSG_PACK_BYTES);

        // then
        assertThat(response.getEvent()).containsEntry("eventType", "UPDATE_PAYLOAD_REJECTED");

        testClient.receiveSingleEvent(workflowInstanceEvents("UPDATE_PAYLOAD_REJECTED"));
    }

    @Test
    public void shouldRejectUpdateForNonExistingWorkflowInstance() throws Exception
    {
        // when
        final ExecuteCommandResponse response = updatePayload(-1L, -1L, MSG_PACK_BYTES);

        // then
        assertThat(response.getEvent()).containsEntry("eventType", "UPDATE_PAYLOAD_REJECTED");

        testClient.receiveSingleEvent(workflowInstanceEvents("UPDATE_PAYLOAD_REJECTED"));
    }

    @Test
    public void shouldRejectUpdateForCompletedWorkflowInstance() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent activityInstanceEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        testClient.completeTaskOfType("task-1", MSG_PACK_BYTES);

        testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));
        testClient.completeTaskOfType("task-2");

        testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_COMPLETED"));

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey, activityInstanceEvent.key(), MSG_PACK_BYTES);

        // then
        assertThat(response.getEvent()).containsEntry("eventType", "UPDATE_PAYLOAD_REJECTED");

        testClient.receiveSingleEvent(workflowInstanceEvents("UPDATE_PAYLOAD_REJECTED"));
    }

    private ExecuteCommandResponse updatePayload(final long workflowInstanceKey, final long activityInstanceKey, byte[] payload) throws Exception
    {
        return apiRule.createCmdRequest()
            .topicName(ClientApiRule.DEFAULT_TOPIC_NAME)
            .partitionId(ClientApiRule.DEFAULT_PARTITION_ID)
            .eventTypeWorkflow()
            .key(activityInstanceKey)
            .command()
                .put("eventType", "UPDATE_PAYLOAD")
                .put("workflowInstanceKey", workflowInstanceKey)
                .put("payload", payload)
            .done()
            .sendAndAwait();
    }
}
