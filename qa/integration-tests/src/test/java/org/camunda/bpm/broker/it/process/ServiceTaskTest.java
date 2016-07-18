package org.camunda.bpm.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.bpmn.TngpModelInstance.wrap;
import static org.camunda.tngp.broker.test.util.bpmn.TngpModelInstance.wrapCopy;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.broker.it.TestUtil;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.ProcessService;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.DeployedWorkflowType;
import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.junit.Before;
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

    protected DeployedWorkflowType fooProcess;
    protected DeployedWorkflowType barProcess;
    protected DeployedWorkflowType twoTasksProcess;

    @Before
    public void deployProcess()
    {
        final TngpClient client = clientRule.getClient();
        final ProcessService workflowService = client.processes();

        final BpmnModelInstance fooBpmnModel = Bpmn.createExecutableProcess("anId")
            .startEvent()
            .serviceTask("serviceTask")
            .endEvent()
            .done();

        wrap(fooBpmnModel).taskAttributes("serviceTask", "foo", 0);

        fooProcess = workflowService.deploy()
            .bpmnModelInstance(
                    fooBpmnModel)
            .execute();

        final BpmnModelInstance barBpmnModel = wrapCopy(fooBpmnModel).taskAttributes("serviceTask", "bar", 0);

        barProcess = workflowService.deploy()
                .bpmnModelInstance(
                        barBpmnModel)
                .execute();

        final BpmnModelInstance twoTasksModel = Bpmn.createExecutableProcess("anId")
            .startEvent()
            .serviceTask("serviceTask1")
            .serviceTask("serviceTask2")
            .endEvent()
            .done();

        wrap(twoTasksModel)
            .taskAttributes("serviceTask1", "foo", 0)
            .taskAttributes("serviceTask2", "bar", 0);


        twoTasksProcess = workflowService.deploy()
            .bpmnModelInstance(
                twoTasksModel)
            .execute();
    }

    @Test
    public void shouldStartProcessWithServiceTask()
    {
        final TngpClient client = clientRule.getClient();
        final ProcessService workflowService = client.processes();

        // when
        final WorkflowInstance processInstance = workflowService.start()
            .workflowTypeId(fooProcess.getWorkflowTypeId())
            .execute();

        assertThat(processInstance.getId()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldPollAndLockServiceTask() throws InterruptedException
    {
        final TngpClient client = clientRule.getClient();
        final ProcessService workflowService = client.processes();

        // given
        workflowService.start()
            .workflowTypeId(fooProcess.getWorkflowTypeId())
            .execute();

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
    }

    @Test
    public void shouldNotLockServiceTaskOfDifferentType()
    {
        final TngpClient client = clientRule.getClient();
        final ProcessService workflowService = client.processes();

        // given
        workflowService.start()
            .workflowTypeId(fooProcess.getWorkflowTypeId())
            .execute();

        workflowService.start()
            .workflowTypeId(barProcess.getWorkflowTypeId())
            .execute();

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
        final ProcessService workflowService = client.processes();

        // given
        workflowService.start()
            .workflowTypeId(fooProcess.getWorkflowTypeId())
            .execute();

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
        final ProcessService workflowService = client.processes();

        // given
        workflowService.start()
            .workflowTypeId(twoTasksProcess.getWorkflowTypeId())
            .execute();

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
}
