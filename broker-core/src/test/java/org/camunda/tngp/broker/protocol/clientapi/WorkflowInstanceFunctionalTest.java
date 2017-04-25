package org.camunda.tngp.broker.protocol.clientapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent.PROP_EVENT_TYPE;
import static org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_ACTIVITY_ID;
import static org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_INSTANCE_KEY;
import static org.camunda.tngp.broker.workflow.data.WorkflowInstanceEvent.PROP_WORKFLOW_VERSION;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.taskEvents;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient;
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
        testClient = apiRule.topic(0);
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
        final long workflowInstanceKey = testClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent event = testClient.receiveSingleEvent(workflowInstanceEvents("START_EVENT_OCCURRED"));

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
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

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
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

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
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

        assertThat(event.longKey()).isEqualTo(workflowInstanceKey);
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

        assertThat(event.longKey()).isEqualTo(workflowInstanceKey);
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

        assertThat(event.longKey()).isEqualTo(workflowInstanceKey);
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

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
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

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
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
            .containsKey("activityInstanceKey")
            .containsKey("customHeaders");

        @SuppressWarnings("unchecked")
        final List<Map<String, String>> customHeaders = (List<Map<String, String>>) headers.get("customHeaders");
        assertThat(customHeaders)
            .hasSize(2)
            .extracting(m -> tuple(m.get("key"), m.get("value")))
            .contains(tuple("a", "b"), tuple("c", "d"));
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

        assertThat(activityCompletedEvent.longKey()).isEqualTo(activityActivatedEvent.longKey());
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
                .limit(9)
                .collect(Collectors.toList());

        assertThat(workflowEvents).extracting(e -> e.event().get(PROP_EVENT_TYPE)).containsExactly(

                "CREATE_WORKFLOW_INSTANCE",
                "WORKFLOW_INSTANCE_CREATED",
                "START_EVENT_OCCURRED",
                "SEQUENCE_FLOW_TAKEN",
                "ACTIVITY_ACTIVATED",
                "ACTIVITY_COMPLETED",
                "SEQUENCE_FLOW_TAKEN",
                "END_EVENT_OCCURRED",
                "WORKFLOW_INSTANCE_COMPLETED");
    }

}
