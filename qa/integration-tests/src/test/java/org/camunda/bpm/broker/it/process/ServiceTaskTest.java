package org.camunda.bpm.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.bpmn.TngpModelInstance.wrap;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.broker.it.TestUtil;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.WorkflowsClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.WorkflowDefinition;
import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ServiceTaskTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    public static BpmnModelInstance oneTaskProcess(String taskType)
    {
        return wrap(Bpmn.createExecutableProcess("anId")
            .startEvent()
            .serviceTask("serviceTask")
            .endEvent("endEvent")
            .done())
        .taskAttributes("serviceTask", taskType, 0);
    }

    @Test
    public void shouldStartProcessWithServiceTask()
    {
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        final WorkflowDefinition workflow = clientRule.deployProcess(oneTaskProcess("foo"));

        // when
        final WorkflowInstance workflowInstance = TestUtil.doRepeatedly(() ->
            workflowService
                .start()
                .workflowDefinitionId(workflow.getId())
                .execute())
            .until(
                (wfInstance) -> wfInstance != null,
                (exception) -> !exception.getMessage().contains("(1-3)"));

        assertThat(workflowInstance.getId()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldPollAndLockServiceTask() throws InterruptedException
    {
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        final WorkflowDefinition workflow = clientRule.deployProcess(oneTaskProcess("foo"));

        // given
        final WorkflowInstance workflowInstance = TestUtil.doRepeatedly(() ->
            workflowService
                .start()
                .workflowDefinitionId(workflow.getId())
                .execute())
            .until(
                (wfInstance) -> wfInstance != null,
                (exception) -> !exception.getMessage().contains("(1-3)"));

        // when
        final LockedTasksBatch tasksBatch = TestUtil.doRepeatedly(() ->
            client
                .tasks()
                .pollAndLock()
                .taskQueueId(0)
                .taskType("foo")
                .lockTime(100000L)
                .maxTasks(1)
                .execute())
            .until(
                (tasks) -> !tasks.getLockedTasks().isEmpty());

        // then
        assertThat(tasksBatch.getLockedTasks()).hasSize(1);
        assertThat(tasksBatch.getLockedTasks().get(0).getId()).isGreaterThan(0);
        assertThat(tasksBatch.getLockedTasks().get(0).getPayloadString()).isEmpty();
        assertThat(tasksBatch.getLockedTasks().get(0).getWorkflowInstanceId()).isEqualTo(workflowInstance.getId());
    }

    @Test
    public void shouldNotLockServiceTaskOfDifferentType()
    {
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        // given
        final WorkflowDefinition workflow1 = clientRule.deployProcess(oneTaskProcess("foo"));
        final WorkflowDefinition workflow2 = clientRule.deployProcess(oneTaskProcess("bar"));

        TestUtil.doRepeatedly(() ->
            workflowService
                .start()
                .workflowDefinitionId(workflow1.getId())
                .execute())
            .until(
                (wfInstance) -> wfInstance != null,
                (exception) -> !exception.getMessage().contains("(1-3)"));

        TestUtil.doRepeatedly(() ->
            workflowService
                .start()
                .workflowDefinitionId(workflow2.getId())
                .execute())
            .until(
                (wfInstance) -> wfInstance != null,
                (exception) -> !exception.getMessage().contains("(1-3)"));

        // when
        final LockedTasksBatch tasksBatch = TestUtil.doRepeatedly(() ->
            client
                .tasks()
                .pollAndLock()
                .taskQueueId(0)
                .taskType("bar")
                .lockTime(100000L)
                .maxTasks(2)
                .execute())
            .until(
                (tasks) -> !tasks.getLockedTasks().isEmpty());

        // then
        assertThat(tasksBatch.getLockedTasks()).hasSize(1);
        assertThat(tasksBatch.getLockedTasks().get(0).getId()).isGreaterThan(0);
        assertThat(tasksBatch.getLockedTasks().get(0).getPayloadString()).isEmpty();
    }

    @Test
    public void shouldCompleteServiceTask()
    {
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        // given
        final WorkflowDefinition workflow = clientRule.deployProcess(oneTaskProcess("foo"));

        TestUtil.doRepeatedly(() ->
            workflowService
                .start()
                .workflowDefinitionId(workflow.getId())
                .execute())
            .until(
                (wfInstance) -> wfInstance != null,
                (exception) -> !exception.getMessage().contains("(1-3)"));

        final LockedTasksBatch tasksBatch = TestUtil.doRepeatedly(() ->
            client
                .tasks()
                .pollAndLock()
                .taskQueueId(0)
                .taskType("foo")
                .lockTime(100000L)
                .maxTasks(1)
                .execute())
            .until(
                (tasks) -> !tasks.getLockedTasks().isEmpty());

        // when
        final LockedTask task = tasksBatch.getLockedTasks().get(0);
        final Long result = client.tasks().complete()
            .taskQueueId(0)
            .taskId(task.getId())
            .execute();

        // then
        assertThat(result).isEqualTo(task.getId());
    }

    @Test
    public void shouldExecuteSequenceOfServiceTasks()
    {
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        // given
        final WorkflowDefinition workflow = clientRule.deployProcess(ProcessModels.TWO_TASKS_PROCESS);

        TestUtil.doRepeatedly(() ->
            workflowService
                .start()
                .workflowDefinitionId(workflow.getId())
                .execute())
            .until(
                (wfInstance) -> wfInstance != null,
                (exception) -> !exception.getMessage().contains("(1-3)"));

        final LockedTasksBatch task1Batch = TestUtil.doRepeatedly(() ->
            client
                .tasks()
                .pollAndLock()
                .taskQueueId(0)
                .taskType("foo")
                .lockTime(100000L)
                .maxTasks(5)
                .execute())
            .until(
                (tasks) -> !tasks.getLockedTasks().isEmpty());

        final LockedTask task1 = task1Batch.getLockedTasks().get(0);
        client.tasks().complete()
            .taskQueueId(0)
            .taskId(task1.getId())
            .execute();


        // when
        final LockedTasksBatch task2Batch = TestUtil.doRepeatedly(() ->
            client
                .tasks()
                .pollAndLock()
                .taskQueueId(0)
                .taskType("bar")
                .lockTime(100000L)
                .maxTasks(5)
                .execute())
            .until(
                (tasks) -> !tasks.getLockedTasks().isEmpty());

        // then
        assertThat(task2Batch.getLockedTasks()).hasSize(1);

        final LockedTask task2 = task2Batch.getLockedTasks().get(0);

        final Long result = client.tasks().complete()
            .taskQueueId(0)
            .taskId(task2.getId())
            .execute();

        // then
        assertThat(result).isEqualTo(task2.getId());
    }

    @Test
    public void shouldExecuteServiceTaskWithoutOutgoingFlow()
    {
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        final WorkflowDefinition workflow = clientRule.deployProcess(
                wrap(oneTaskProcess("foo"))
                    .removeFlowNode("endEvent"));

        // given
        TestUtil.doRepeatedly(() ->
            workflowService
                .start()
                .workflowDefinitionId(workflow.getId())
                .execute())
            .until(
                (wfInstance) -> wfInstance != null,
                (exception) -> !exception.getMessage().contains("(1-3)"));

        // when
        final LockedTasksBatch tasksBatch = TestUtil.doRepeatedly(() ->
            client
                .tasks()
                .pollAndLock()
                .taskQueueId(0)
                .taskType("foo")
                .lockTime(100000L)
                .maxTasks(1)
                .execute())
            .until(
                (tasks) -> !tasks.getLockedTasks().isEmpty());

        // then
        assertThat(tasksBatch.getLockedTasks()).hasSize(1);

        final LockedTask task = tasksBatch.getLockedTasks().get(0);
        assertThat(task.getId()).isGreaterThan(0);
        assertThat(task.getPayloadString()).isEmpty();

        final Long result = client
            .tasks()
            .complete()
            .taskQueueId(0)
            .taskId(task.getId())
            .execute();

        assertThat(result).isEqualTo(task.getId());
    }
}
