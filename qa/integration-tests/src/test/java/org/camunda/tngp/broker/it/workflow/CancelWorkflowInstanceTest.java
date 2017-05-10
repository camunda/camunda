package org.camunda.tngp.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.it.util.RecordingTaskEventHandler.eventType;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.util.TestUtil.waitUntil;

import java.time.Duration;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.util.RecordingTaskEventHandler;
import org.camunda.tngp.broker.it.util.WorkflowInstanceEventRecorder;
import org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TngpModelInstance;
import org.camunda.tngp.client.ClientCommandRejectedException;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEventType;
import org.camunda.tngp.client.task.PollableTaskSubscription;
import org.camunda.tngp.client.task.Task;
import org.camunda.tngp.client.workflow.cmd.WorkflowInstance;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest
{
    private static final TngpModelInstance WORKFLOW = wrap(Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task")
            .endEvent("end")
            .done())
                .taskDefinition("task", "test", 3);

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
    public void shouldCancelWorkflowInstance()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        // when
        clientRule.workflowTopic().cancel()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .execute();

        // then
        waitUntil(() -> workflowInstanceEventRecoder.getEventTypes().contains("WORKFLOW_INSTANCE_CANCELED"));
    }

    @Test
    public void shouldFailCancelNotExistingWorkflowInstance()
    {
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Failed to cancel workflow instance with key '3'.");

        // when
        clientRule.workflowTopic().cancel()
            .workflowInstanceKey(3L)
            .execute();
    }

    @Test
    public void shouldFailToCompleteTaskAfterCancel()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        final PollableTaskSubscription taskSubscription = clientRule.taskTopic().newPollableTaskSubscription()
            .taskType("test")
            .lockOwner(1)
            .lockTime(Duration.ofMinutes(1))
            .open();

        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.LOCKED)));

        clientRule.workflowTopic().cancel()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .execute();

        // when
        taskSubscription.poll(Task::complete);

        // then
        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.COMPLETE_REJECTED)));

        assertThat(recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.CANCELED)));
        assertThat(workflowInstanceEventRecoder.getEventTypes()).contains("WORKFLOW_INSTANCE_CANCELED");
    }

    @Test
    public void shouldFailToLockTaskAfterCancel()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.CREATED)));

        clientRule.workflowTopic().cancel()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .execute();

        final PollableTaskSubscription taskSubscription = clientRule.taskTopic().newPollableTaskSubscription()
                .taskType("test")
                .lockOwner(1)
                .lockTime(Duration.ofMinutes(1))
                .open();

        // when
        final int completedTasks = taskSubscription.poll(Task::complete);

        // then
        assertThat(completedTasks).isEqualTo(0);

        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.LOCK_REJECTED)));

        assertThat(recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.CANCELED)));
        assertThat(workflowInstanceEventRecoder.getEventTypes()).contains("WORKFLOW_INSTANCE_CANCELED");
    }

}
