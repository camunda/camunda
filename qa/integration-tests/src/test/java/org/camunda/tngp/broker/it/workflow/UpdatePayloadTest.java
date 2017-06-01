package org.camunda.tngp.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.it.util.RecordingTaskEventHandler.taskType;
import static org.camunda.tngp.broker.it.util.WorkflowInstanceEventRecorder.eventType;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.util.TestUtil.waitUntil;

import java.time.Duration;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.util.RecordingTaskEventHandler;
import org.camunda.tngp.broker.it.util.WorkflowInstanceEventRecorder;
import org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TngpModelInstance;
import org.camunda.tngp.client.cmd.ClientCommandRejectedException;
import org.camunda.tngp.client.event.TaskEvent;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.client.workflow.cmd.WorkflowInstance;
import org.camunda.tngp.client.workflow.impl.WorkflowInstanceEventType;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class UpdatePayloadTest
{
    private static final String PAYLOAD = "{\"foo\": \"bar\"}";

    private static final TngpModelInstance WORKFLOW = wrap(Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task-1")
            .serviceTask("task-2")
            .endEvent("end")
            .done())
                .taskDefinition("task-1", "task-1", 3)
                .taskDefinition("task-2", "task-2", 3)
                .ioMapping("task-1")
                    .output("$.result", "$.result")
                    .done();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public RecordingTaskEventHandler recordingTaskEventHandler = new RecordingTaskEventHandler(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(recordingTaskEventHandler);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private WorkflowInstanceEventRecorder workflowInstanceEventRecoder;
    private TopicSubscription workflowInstanceSubscription;

    @Before
    public void init()
    {
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();

        workflowInstanceEventRecoder = new WorkflowInstanceEventRecorder();

        workflowInstanceSubscription = clientRule.topic().newSubscription()
            .name("workflow")
            .startAtHeadOfTopic()
            .handler(workflowInstanceEventRecoder)
            .open();
    }

    @After
    public void cleanUp()
    {
        workflowInstanceSubscription.close();
    }

    @Test
    public void shouldUpdatePayloadWhenActivityIsActivated()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> workflowInstanceEventRecoder.hasWorkflowEvent(eventType(WorkflowInstanceEventType.ACTIVITY_ACTIVATED)));

        final long activtyInstanceKey = workflowInstanceEventRecoder
                .getSingleWorkflowEvent(eventType(WorkflowInstanceEventType.ACTIVITY_ACTIVATED))
                .getKey();

        // when
        clientRule.workflowTopic().updatePayload()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .activityInstanceKey(activtyInstanceKey)
            .payload(PAYLOAD)
            .execute();

        // then
        waitUntil(() -> workflowInstanceEventRecoder.hasWorkflowEvent(eventType(WorkflowInstanceEventType.PAYLOAD_UPDATED)));

        clientRule.taskTopic().newTaskSubscription()
            .taskType("task-1")
            .lockOwner(1)
            .lockTime(Duration.ofMinutes(5))
            .handler(task ->
            {
                task.setPayload("{\"result\": \"ok\"}");
                task.complete();
            })
            .open();

        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(taskType("task-2")));

        final TaskEvent task2 = recordingTaskEventHandler.getTaskEvents(taskType("task-2")).get(0);
        assertThat(task2.getPayload()).isEqualTo("{\"foo\":\"bar\",\"result\":\"ok\"}");
    }

    @Test
    public void shouldFailUpdatePayloadIfActivityIsCompleted()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        clientRule.taskTopic().newTaskSubscription()
            .taskType("task-1")
            .lockOwner(1)
            .lockTime(Duration.ofMinutes(5))
            .handler(task ->
            {
                task.setPayload("{\"result\": \"done\"}");
                task.complete();
            })
            .open();

        waitUntil(() -> workflowInstanceEventRecoder.hasWorkflowEvent(eventType(WorkflowInstanceEventType.ACTIVITY_COMPLETED)));

        final long activityInstanceKey = workflowInstanceEventRecoder
                .getSingleWorkflowEvent(eventType(WorkflowInstanceEventType.ACTIVITY_COMPLETED))
                .getKey();

        // then
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Failed to update payload");

        // when
        clientRule.workflowTopic().updatePayload()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .activityInstanceKey(activityInstanceKey)
            .payload(PAYLOAD)
            .execute();

    }

}
