package org.camunda.tngp.broker.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.*;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;

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
                    .output("$.jsonObject", "$.obj")
                    .done();

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

        testClient.completeTaskOfType("task-1", MSGPACK_PAYLOAD);

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        final byte[] payload = (byte[]) activityCompletedEvent.event().get("payload");

        assertThat(MSGPACK_MAPPER.readTree(payload))
            .isEqualTo(JSON_MAPPER.readTree("{'obj':{'testAttr':'test'}, 'b':'wf'}"));
    }

    @Test
    public void shouldRejectUpdateForInvalidPayload() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW);
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");
        final SubscribedEvent activityInstanceEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey, activityInstanceEvent.key(),
                                                              MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("'foo'")));

        // then
        assertThat(response.getEvent()).containsEntry("eventType", "UPDATE_PAYLOAD_REJECTED");

        testClient.receiveSingleEvent(workflowInstanceEvents("UPDATE_PAYLOAD_REJECTED"));
    }

    @Test
    public void shouldRejectUpdateForCompletedActivity() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeTaskOfType("task-1", MSGPACK_PAYLOAD);

        final SubscribedEvent activityInstanceEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey, activityInstanceEvent.key(), MSGPACK_PAYLOAD);

        // then
        assertThat(response.getEvent()).containsEntry("eventType", "UPDATE_PAYLOAD_REJECTED");

        testClient.receiveSingleEvent(workflowInstanceEvents("UPDATE_PAYLOAD_REJECTED"));
    }

    @Test
    public void shouldRejectUpdateForNonExistingWorkflowInstance() throws Exception
    {
        // when
        final ExecuteCommandResponse response = updatePayload(-1L, -1L, MSGPACK_PAYLOAD);

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

        testClient.completeTaskOfType("task-1", MSGPACK_PAYLOAD);

        testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));
        testClient.completeTaskOfType("task-2");

        testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_COMPLETED"));

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey, activityInstanceEvent.key(), MSGPACK_PAYLOAD);

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
