package org.camunda.tngp.broker.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.asMap;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.createMsgPack;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;
import static org.camunda.tngp.util.buffer.BufferUtil.bufferAsArray;

import org.agrona.DirectBuffer;
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
                    .output("$.a", "$.a")
                    .done();

    private static final DirectBuffer PAYLOAD = createMsgPack().put("a", "foo").done();

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
    public void shouldUpdatePayload()
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent activityInstanceEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey, activityInstanceEvent.key(), createMsgPack().put("foo", "bar").done());

        // then
        assertThat(response.getEvent()).containsEntry("eventType", "PAYLOAD_UPDATED");

        final SubscribedEvent updatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("PAYLOAD_UPDATED"));

        assertThat(updatedEvent.key()).isEqualTo(activityInstanceEvent.key());
        assertThat(updatedEvent.event()).containsEntry("workflowInstanceKey", workflowInstanceKey);

        assertThat(asMap((byte[]) updatedEvent.event().get("payload"))).containsEntry("foo", "bar");
    }

    @Test
    public void shouldUpdatePayloadWhenActivityActivated()
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent activityInstanceEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        // when
        updatePayload(workflowInstanceKey, activityInstanceEvent.key(), createMsgPack().put("b", "wf").done());

        testClient.receiveSingleEvent(workflowInstanceEvents("PAYLOAD_UPDATED"));

        testClient.completeTaskOfType("task-1", createMsgPack().put("a", "task").done());

        // then
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        final byte[] payload = (byte[]) activityCompletedEvent.event().get("payload");
        assertThat(asMap(payload))
            .containsEntry("a", "task")
            .containsEntry("b", "wf");
    }

    @Test
    public void shouldRejectUpdateForCompletedActivity()
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeTaskOfType("task-1", PAYLOAD);

        final SubscribedEvent activityInstanceEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey, activityInstanceEvent.key(), PAYLOAD);

        // then
        assertThat(response.getEvent()).containsEntry("eventType", "UPDATE_PAYLOAD_REJECTED");

        testClient.receiveSingleEvent(workflowInstanceEvents("UPDATE_PAYLOAD_REJECTED"));
    }

    @Test
    public void shouldRejectUpdateForNonExistingWorkflowInstance()
    {
        // when
        final ExecuteCommandResponse response = updatePayload(-1L, -1L, PAYLOAD);

        // then
        assertThat(response.getEvent()).containsEntry("eventType", "UPDATE_PAYLOAD_REJECTED");

        testClient.receiveSingleEvent(workflowInstanceEvents("UPDATE_PAYLOAD_REJECTED"));
    }

    @Test
    public void shouldRejectUpdateForCompletedWorkflowInstance()
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent activityInstanceEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        testClient.completeTaskOfType("task-1", PAYLOAD);

        testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));
        testClient.completeTaskOfType("task-2");

        testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_COMPLETED"));

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey, activityInstanceEvent.key(), PAYLOAD);

        // then
        assertThat(response.getEvent()).containsEntry("eventType", "UPDATE_PAYLOAD_REJECTED");

        testClient.receiveSingleEvent(workflowInstanceEvents("UPDATE_PAYLOAD_REJECTED"));
    }

    private ExecuteCommandResponse updatePayload(final long workflowInstanceKey, final long activityInstanceKey, DirectBuffer payload)
    {
        return apiRule.createCmdRequest()
            .topicName(ClientApiRule.DEFAULT_TOPIC_NAME)
            .partitionId(ClientApiRule.DEFAULT_PARTITION_ID)
            .eventTypeWorkflow()
            .key(activityInstanceKey)
            .command()
                .put("eventType", "UPDATE_PAYLOAD")
                .put("workflowInstanceKey", workflowInstanceKey)
                .put("payload", bufferAsArray(payload))
            .done()
            .sendAndAwait();
    }
}
