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
import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_EVENT_TYPE;
import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_ACTIVITY_ID;
import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_INSTANCE_KEY;
import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_VERSION;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.taskEvents;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedEvent;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest
{
    private static final BpmnModelInstance WORKFLOW = wrap(
            Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task")
            .endEvent()
            .done())
                .taskDefinition("task", "test", 5);

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
    public void shouldCancelWorkflowInstance()
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.receiveEvents(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        // when
        final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

        // then
        assertThat(response.getEvent()).containsEntry("state", "WORKFLOW_INSTANCE_CANCELED");

        final SubscribedEvent workflowInstanceCanceledEvent = testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_CANCELED"));

        assertThat(workflowInstanceCanceledEvent.key()).isEqualTo(workflowInstanceKey);
        assertThat(workflowInstanceCanceledEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");

        final List<SubscribedEvent> workflowEvents = testClient
                .receiveEvents(workflowInstanceEvents())
                .limit(9)
                .collect(Collectors.toList());

        assertThat(workflowEvents).extracting(e -> e.event().get(PROP_EVENT_TYPE)).containsExactly(
                "CREATE_WORKFLOW_INSTANCE",
                "WORKFLOW_INSTANCE_CREATED",
                "START_EVENT_OCCURRED",
                "SEQUENCE_FLOW_TAKEN",
                "ACTIVITY_READY",
                "ACTIVITY_ACTIVATED",
                "CANCEL_WORKFLOW_INSTANCE",
                "ACTIVITY_TERMINATED",
                "WORKFLOW_INSTANCE_CANCELED");
    }

    @Test
    public void shouldCancelActivityInstance()
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

        // then
        assertThat(response.getEvent()).containsEntry("state", "WORKFLOW_INSTANCE_CANCELED");

        final SubscribedEvent activityTerminatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_TERMINATED"));

        assertThat(activityTerminatedEvent.key()).isEqualTo(activityActivatedEvent.key());
        assertThat(activityTerminatedEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "task");
    }

    @Test
    public void shouldCancelTaskForActivity()
    {
        // given
        testClient.deploy(WORKFLOW);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        final SubscribedEvent taskCreatedEvent = testClient.receiveSingleEvent(taskEvents("CREATED"));

        final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

        // then
        assertThat(response.getEvent()).containsEntry("state", "WORKFLOW_INSTANCE_CANCELED");

        final SubscribedEvent taskCanceledEvent = testClient.receiveSingleEvent(taskEvents("CANCELED"));

        assertThat(taskCanceledEvent.key()).isEqualTo(taskCreatedEvent.key());

        @SuppressWarnings("unchecked")
        final Map<String, Object> headers = (Map<String, Object>) taskCanceledEvent.event().get("headers");
        assertThat(headers)
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowDefinitionVersion", 1)
            .containsEntry("activityId", "task");
    }

    @Test
    public void shouldRejectCancelNonExistingWorkflowInstance()
    {
        // when
        final ExecuteCommandResponse response = cancelWorkflowInstance(-1L);

        // then
        assertThat(response.getEvent()).containsEntry("state", "CANCEL_WORKFLOW_INSTANCE_REJECTED");

        testClient.receiveSingleEvent(workflowInstanceEvents("CANCEL_WORKFLOW_INSTANCE_REJECTED"));
    }

    @Test
    public void shouldRejectCancelCompletedWorkflowInstance()
    {
        // given
        testClient.deploy(Bpmn.createExecutableProcess("process")
                .startEvent()
                .endEvent()
                .done());

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_COMPLETED"));

        // when
        final ExecuteCommandResponse response = cancelWorkflowInstance(workflowInstanceKey);

        // then
        assertThat(response.getEvent()).containsEntry("state", "CANCEL_WORKFLOW_INSTANCE_REJECTED");

        testClient.receiveSingleEvent(workflowInstanceEvents("CANCEL_WORKFLOW_INSTANCE_REJECTED"));
    }

    private ExecuteCommandResponse cancelWorkflowInstance(final long workflowInstanceKey)
    {
        return apiRule.createCmdRequest()
            .topicName(ClientApiRule.DEFAULT_TOPIC_NAME)
            .partitionId(ClientApiRule.DEFAULT_PARTITION_ID)
            .eventTypeWorkflow()
            .key(workflowInstanceKey)
            .command()
                .put("state", "CANCEL_WORKFLOW_INSTANCE")
            .done()
            .sendAndAwait();
    }
}
