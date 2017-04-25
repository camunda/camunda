package org.camunda.tngp.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.it.util.RecordingTaskEventHandler.eventType;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.util.TestUtil.waitUntil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.util.RecordingTaskEventHandler;
import org.camunda.tngp.broker.it.util.RecordingTaskHandler;
import org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.TngpModelInstance;
import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.client.event.TopicEventType;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEventType;
import org.camunda.tngp.client.task.Task;
import org.camunda.tngp.client.workflow.cmd.WorkflowInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ServiceTaskTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public RecordingTaskEventHandler recordingTaskEventHandler = new RecordingTaskEventHandler(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(recordingTaskEventHandler);

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

    private static TngpModelInstance oneTaskProcess(String taskType)
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
        Bpmn.writeModelToStream(System.out, oneTaskProcess("foo"));

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
            .lockOwner(1)
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
            .lockOwner(1)
            .lockTime(Duration.ofMinutes(5))
            .handler(Task::complete)
            .open();

        // then
        waitUntil(() -> recordingTaskEventHandler.hasTaskEvent(eventType(TaskEventType.COMPLETED)));

        // TODO use workflow topic subscription to verify that workflow instance is completed
        final List<String> workflowEventTypes = new ArrayList<>();
        clientRule.topic().newSubscription()
            .name("test")
            .startAtHeadOfTopic()
            .handler((meta, event) ->
            {
                if (meta.getEventType() == TopicEventType.WORKFLOW)
                {
                    final Matcher matcher = Pattern.compile("\"eventType\":\"(\\w+)\"").matcher(event.getJson());
                    if (matcher.find())
                    {
                        final String eventType = matcher.group(1);
                        workflowEventTypes.add(eventType);
                    }
                }
            }).open();

        waitUntil(() -> workflowEventTypes.contains("WORKFLOW_INSTANCE_COMPLETED"));
    }

}
