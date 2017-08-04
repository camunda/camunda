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

import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.*;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.taskEvents;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedEvent;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowInstanceFunctionalTest
{
    private static final String PROP_TASK_TYPE = "type";
    private static final String PROP_TASK_RETRIES = "retries";

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
    public void shouldStartWorkflowInstanceAtNoneStartEvent()
    {
        // given
        testClient.deploy(Bpmn.createExecutableProcess("process")
                .startEvent("foo")
                .endEvent()
                .done());

        // when
        final ExecuteCommandResponse response = testClient.createWorkflowInstanceWithResponse("process");

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(workflowInstanceEvents("START_EVENT_OCCURRED"));

        final long workflowInstanceKey = response.key();
        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.position()).isGreaterThan(response.position());
        assertThat(event.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
    }

    @Test
    public void shouldTakeSequenceFlowFromStartEvent()
    {
        // given
        testClient.deploy(Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .sequenceFlowId("foo")
                    .endEvent()
                    .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(workflowInstanceEvents("SEQUENCE_FLOW_TAKEN"));

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
    }

    @Test
    public void shouldOccureEndEvent()
    {
        // given
        testClient.deploy(Bpmn.createExecutableProcess("process")
                .startEvent()
                .endEvent("foo")
                .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(workflowInstanceEvents("END_EVENT_OCCURRED"));

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
    }

    @Test
    public void shouldCompleteWorkflowInstance()
    {
        // given
        testClient.deploy(Bpmn.createExecutableProcess("process")
                .startEvent()
                .endEvent()
                .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_COMPLETED"));

        assertThat(event.key()).isEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");
    }

    @Test
    public void shouldConsumeTokenIfEventHasNoOutgoingSequenceflow()
    {
        // given
        testClient.deploy(Bpmn.createExecutableProcess("process")
                .startEvent()
                .done());

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_COMPLETED"));

        assertThat(event.key()).isEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");
    }

    @Test
    public void shouldConsumeTokenIfActivityHasNoOutgoingSequenceflow()
    {
        // given
        final BpmnModelInstance modelInstance = wrap(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("foo")
                    .done())
                        .taskDefinition("foo", "bar", 5);

        testClient.deploy(modelInstance);

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        testClient.completeTaskOfType("bar");

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(workflowInstanceEvents("WORKFLOW_INSTANCE_COMPLETED"));

        assertThat(event.key()).isEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "");
    }

    @Test
    public void shouldActivateServiceTask()
    {
        // given
        testClient.deploy(wrap(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("foo")
                    .endEvent()
                    .done())
                        .taskDefinition("foo", "bar", 5));

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
    }

    @Test
    public void shouldCreateTaskWhenServiceTaskIsActivated()
    {
        // given
        testClient.deploy(wrap(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("foo")
                    .endEvent()
                    .done())
                        .taskDefinition("foo", "bar", 5));

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        assertThat(event.key()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry(PROP_TASK_TYPE, "bar")
            .containsEntry(PROP_TASK_RETRIES, 5);
    }

    @Test
    public void shouldCreateTaskWithWorkflowInstanceAndCustomHeaders()
    {
        // given
        final Map<String, String> taskHeaders = new HashMap<>();
        taskHeaders.put("a", "b");
        taskHeaders.put("c", "d");

        testClient.deploy(wrap(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("foo")
                    .endEvent()
                    .done())
                        .taskDefinition("foo", "bar", 5)
                        .taskHeaders("foo", taskHeaders));

        // when
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(taskEvents("CREATE"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> headers = (Map<String, Object>) event.event().get("headers");
        assertThat(headers)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry("workflowDefinitionVersion", 1)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo")
            .containsKey("activityInstanceKey");

        final Map<String, Object> customHeaders = (Map<String, Object>) event.event().get("customHeaders");
        assertThat(customHeaders)
            .containsEntry("a", "b")
            .containsEntry("c", "d");
    }

    @Test
    public void shouldCompleteServiceTaskWhenTaskIsCompleted()
    {
        // given
        final BpmnModelInstance modelInstance = wrap(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("foo")
                    .endEvent()
                    .done())
                        .taskDefinition("foo", "bar", 5);

        testClient.deploy(modelInstance);

        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // when
        testClient.completeTaskOfType("bar");

        // then
        final SubscribedEvent activityActivatedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_ACTIVATED"));
        final SubscribedEvent activityCompletedEvent = testClient.receiveSingleEvent(workflowInstanceEvents("ACTIVITY_COMPLETED"));

        assertThat(activityCompletedEvent.key()).isEqualTo(activityActivatedEvent.key());
        assertThat(activityCompletedEvent.event())
            .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "process")
            .containsEntry(PROP_WORKFLOW_VERSION, 1)
            .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
            .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "foo");
    }

    @Test
    public void shouldCreateAndCompleteWorkflowInstance()
    {
        // given
        final BpmnModelInstance modelInstance = wrap(
                Bpmn.createExecutableProcess("process")
                    .startEvent("a")
                    .serviceTask("b")
                    .endEvent("c")
                    .done())
                        .taskDefinition("b", "foo", 5);

        testClient.deploy(modelInstance);

        testClient.createWorkflowInstance("process");

        // when
        testClient.completeTaskOfType("foo");

        // then
        final List<SubscribedEvent> workflowEvents = testClient
                .receiveEvents(workflowInstanceEvents())
                .limit(11)
                .collect(Collectors.toList());

        assertThat(workflowEvents).extracting(e -> e.event().get(PROP_EVENT_TYPE)).containsExactly(

                "CREATE_WORKFLOW_INSTANCE",
                "WORKFLOW_INSTANCE_CREATED",
                "START_EVENT_OCCURRED",
                "SEQUENCE_FLOW_TAKEN",
                "ACTIVITY_READY",
                "ACTIVITY_ACTIVATED",
                "ACTIVITY_COMPLETING",
                "ACTIVITY_COMPLETED",
                "SEQUENCE_FLOW_TAKEN",
                "END_EVENT_OCCURRED",
                "WORKFLOW_INSTANCE_COMPLETED");
    }

}
