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

import static io.zeebe.broker.test.MsgPackUtil.JSON_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_PAYLOAD;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;

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

        final SubscribedRecord activityInstanceEvent = waitForActivityActivatedEvent();

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey,
                                                              activityInstanceEvent.key(),
                                                              MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}")));

        // then
        assertThat(response.intent()).isEqualTo(WorkflowInstanceIntent.PAYLOAD_UPDATED);

        final SubscribedRecord updatedEvent = testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.PAYLOAD_UPDATED)
            .getFirst();


        assertThat(updatedEvent.position()).isGreaterThan(response.position());
        assertThat(updatedEvent.key()).isEqualTo(activityInstanceEvent.key());
        assertThat(updatedEvent.value()).containsEntry("workflowInstanceKey", workflowInstanceKey);

        final byte[] payload = (byte[]) updatedEvent.value().get("payload");

        assertThat(MSGPACK_MAPPER.readTree(payload))
            .isEqualTo(JSON_MAPPER.readTree("{'foo':'bar'}"));
    }

    @Test
    public void shouldUpdatePayloadWhenActivityActivated() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedRecord activityInstanceEvent = waitForActivityActivatedEvent();

        // when
        updatePayload(workflowInstanceKey, activityInstanceEvent.key(),
                      MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'b':'wf'}")));

        testClient.receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.PAYLOAD_UPDATED)
            .getFirst();

        testClient.completeJobOfType("task-1", MSGPACK_PAYLOAD);

        // then
        final SubscribedRecord activityCompletedEvent = waitForActivityCompletedEvent();

        final byte[] payload = (byte[]) activityCompletedEvent.value().get("payload");

        assertThat(MSGPACK_MAPPER.readTree(payload))
            .isEqualTo(JSON_MAPPER.readTree("{'obj':{'testAttr':'test'}, 'b':'wf'}"));
    }

    @Test
    public void shouldRejectUpdateForInvalidPayload() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW);
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");
        final SubscribedRecord activityInstanceEvent = waitForActivityActivatedEvent();

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey, activityInstanceEvent.key(),
                                                              MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("'foo'")));

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);

        final SubscribedRecord rejection = testClient.receiveRejections()
            .withIntent(WorkflowInstanceIntent.UPDATE_PAYLOAD)
            .getFirst();

        assertThat(rejection).isNotNull();
    }

    @Test
    public void shouldRejectUpdateForNonExistingWorkflowInstance() throws Exception
    {
        // when
        final ExecuteCommandResponse response = updatePayload(-1L, -1L, MSGPACK_PAYLOAD);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);

        final SubscribedRecord rejection = testClient.receiveRejections()
            .withIntent(WorkflowInstanceIntent.UPDATE_PAYLOAD)
            .getFirst();

        assertThat(rejection).isNotNull();
    }

    @Test
    public void shouldRejectUpdateForCompletedWorkflowInstance() throws Exception
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedRecord activityInstanceEvent = waitForActivityActivatedEvent();

        testClient.completeJobOfType("task-1", MSGPACK_PAYLOAD);

        waitForActivityCompletedEvent();
        testClient.completeJobOfType("task-2");

        waitForWorkflowInstanceCompletedEvent();

        // when
        final ExecuteCommandResponse response = updatePayload(workflowInstanceKey, activityInstanceEvent.key(), MSGPACK_PAYLOAD);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);

        final SubscribedRecord rejection = testClient.receiveRejections()
            .withIntent(WorkflowInstanceIntent.UPDATE_PAYLOAD)
            .getFirst();

        assertThat(rejection).isNotNull();
    }

    private SubscribedRecord waitForWorkflowInstanceCompletedEvent()
    {
        return testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.COMPLETED)
            .getFirst();
    }

    private SubscribedRecord waitForActivityCompletedEvent()
    {
        return testClient
            .receiveEvents()
            .ofTypeWorkflowInstance()
            .withIntent(WorkflowInstanceIntent.ACTIVITY_COMPLETED)
            .getFirst();
    }

    private SubscribedRecord waitForActivityActivatedEvent()
    {
        return testClient
                .receiveEvents()
                .ofTypeWorkflowInstance()
                .withIntent(WorkflowInstanceIntent.ACTIVITY_ACTIVATED)
                .getFirst();
    }

    private ExecuteCommandResponse updatePayload(final long workflowInstanceKey, final long activityInstanceKey, byte[] payload) throws Exception
    {
        return apiRule.createCmdRequest()
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.UPDATE_PAYLOAD)
            .key(activityInstanceKey)
            .command()
                .put("workflowInstanceKey", workflowInstanceKey)
                .put("payload", payload)
            .done()
            .sendAndAwait();
    }
}
