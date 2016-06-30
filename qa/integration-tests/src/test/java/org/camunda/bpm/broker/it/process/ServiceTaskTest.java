package org.camunda.bpm.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.bpmn.TngpModelInstance.wrap;
import static org.camunda.tngp.broker.test.util.bpmn.TngpModelInstance.wrapCopy;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.ProcessService;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.DeployedWorkflowType;
import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.junit.Before;
import org.junit.Ignore;
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
    public void shouldPollAndLockServiceTask()
    {
        final TngpClient client = clientRule.getClient();
        final ProcessService workflowService = client.processes();

        // given
        workflowService.start()
            .workflowTypeId(fooProcess.getWorkflowTypeId())
            .execute();

        // when
        // TODO: the task is not guaranteed to exist yet

        final LockedTasksBatch tasksBatch = client
            .tasks()
            .pollAndLock()
            .taskQueueId(0)
            .taskType("foo")
            .lockTime(100000L)
            .maxTasks(1)
            .execute();

        // then
        assertThat(tasksBatch.getLockedTasks()).hasSize(1);
        assertThat(tasksBatch.getLockedTasks().get(0).getId()).isGreaterThan(0);
        assertThat(tasksBatch.getLockedTasks().get(0).getPayloadString()).isEmpty();
    }

    @Test
    @Ignore
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
        // TODO: the task is not guaranteed to exist yet

        final LockedTasksBatch tasksBatch = client
            .tasks()
            .pollAndLock()
            .taskQueueId(0)
            .taskType("bar")
            .lockTime(100000L)
            .maxTasks(2)
            .execute();

        // then
        assertThat(tasksBatch.getLockedTasks()).hasSize(1);
        assertThat(tasksBatch.getLockedTasks().get(0).getId()).isGreaterThan(0);
        assertThat(tasksBatch.getLockedTasks().get(0).getPayloadString()).isEmpty();
    }

    // TODO: test that it is possible to complete such a task
}
