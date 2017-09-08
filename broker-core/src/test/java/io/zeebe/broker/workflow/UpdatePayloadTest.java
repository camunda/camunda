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

import static io.zeebe.broker.test.MsgPackUtil.*;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.test.broker.protocol.clientapi.*;
import org.junit.*;
import org.junit.rules.RuleChain;

public class UpdatePayloadTest
{

    private static final WorkflowDefinition WORKFLOW = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("task-1", t -> t
                         .taskType("task-1")
                         .taskRetries(5)
                         .output("$.jsonObject", "$.obj"))
            .serviceTask("task-2", t -> t
                         .taskType("task-2")
                         .taskRetries(5))
            .endEvent()
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
        assertThat(response.getEvent()).containsEntry("state", "PAYLOAD_UPDATED");

        final SubscribedEvent updatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("PAYLOAD_UPDATED"));

        assertThat(updatedEvent.position()).isGreaterThan(response.position());
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
        assertThat(response.getEvent()).containsEntry("state", "UPDATE_PAYLOAD_REJECTED");

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
        assertThat(response.getEvent()).containsEntry("state", "UPDATE_PAYLOAD_REJECTED");

        testClient.receiveSingleEvent(workflowInstanceEvents("UPDATE_PAYLOAD_REJECTED"));
    }

    @Test
    public void shouldRejectUpdateForNonExistingWorkflowInstance() throws Exception
    {
        // when
        final ExecuteCommandResponse response = updatePayload(-1L, -1L, MSGPACK_PAYLOAD);

        // then
        assertThat(response.getEvent()).containsEntry("state", "UPDATE_PAYLOAD_REJECTED");

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
        assertThat(response.getEvent()).containsEntry("state", "UPDATE_PAYLOAD_REJECTED");

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
                .put("state", "UPDATE_PAYLOAD")
                .put("workflowInstanceKey", workflowInstanceKey)
                .put("payload", payload)
            .done()
            .sendAndAwait();
    }
}
