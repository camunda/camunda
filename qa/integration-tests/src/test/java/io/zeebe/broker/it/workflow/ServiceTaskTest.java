package io.zeebe.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.broker.it.util.TopicEventRecorder.taskEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.wfEvent;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.test.util.TestUtil.waitUntil;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.model.bpmn.Bpmn;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingTaskHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZeebeModelInstance;
import io.zeebe.client.TaskTopicClient;
import io.zeebe.client.WorkflowTopicClient;
import io.zeebe.client.task.Task;
import io.zeebe.client.workflow.cmd.WorkflowInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ServiceTaskTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(eventRecorder);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private WorkflowTopicClient workflowClient;
    private TaskTopicClient taskClient;

    @Before
    public void init()
    {
        workflowClient = clientRule.workflowTopic();
        taskClient = clientRule.taskTopic();
    }

    private static ZeebeModelInstance oneTaskProcess(String taskType)
    {
        return wrap(Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task")
            .endEvent("end")
            .done())
        .taskDefinition("task", taskType, 3);
    }

    @Test
    public void shouldStartWorkflowInstanceWithServiceTask()
    {
        // given
        workflowClient.deploy()
            .bpmnModelInstance(oneTaskProcess("foo"))
            .execute();

        // when
        final WorkflowInstance workflowInstance = workflowClient.create()
                .bpmnProcessId("process")
                .execute();

        // then
        assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);
        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_CREATED")));
    }

    @Test
    public void shouldLockServiceTask()
    {
        // given
        final Map<String, String> taskHeaders = new HashMap<>();
        taskHeaders.put("cust1", "a");
        taskHeaders.put("cust2", "b");

        workflowClient.deploy()
            .bpmnModelInstance(
                    oneTaskProcess("foo")
                        .taskHeaders("task", taskHeaders))
            .execute();

        final WorkflowInstance workflowInstance = workflowClient.create()
                .bpmnProcessId("process")
                .execute();

        // when
        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler();

        taskClient.newTaskSubscription()
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(recordingTaskHandler)
            .open();

        // then
        waitUntil(() -> recordingTaskHandler.getHandledTasks().size() >= 1);

        assertThat(recordingTaskHandler.getHandledTasks()).hasSize(1);

        final Task taskLockedEvent = recordingTaskHandler.getHandledTasks().get(0);
        assertThat(taskLockedEvent.getHeaders())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowDefinitionVersion", 1)
            .containsEntry("workflowInstanceKey", workflowInstance.getWorkflowInstanceKey())
            .containsEntry("activityId", "task")
            .containsEntry("cust1", "a")
            .containsEntry("cust2", "b")
            .containsKey("activityInstanceKey")
            .containsKey("customHeaders");
    }

    @Test
    public void shouldCompleteServiceTask()
    {
        // given
        workflowClient.deploy()
            .bpmnModelInstance(oneTaskProcess("foo"))
            .execute();

        workflowClient.create()
                .bpmnProcessId("process")
                .execute();

        // when
        taskClient.newTaskSubscription()
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(Task::complete)
            .open();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_COMPLETED")));
    }

}
