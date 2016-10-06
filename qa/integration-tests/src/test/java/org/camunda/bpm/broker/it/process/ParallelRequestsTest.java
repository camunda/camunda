package org.camunda.bpm.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.broker.it.TestUtil;
import org.camunda.bpm.broker.it.util.ParallelRequests;
import org.camunda.bpm.broker.it.util.ParallelRequests.SilentFuture;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.AsyncTasksClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowsClient;
import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.client.cmd.WorkflowDefinition;
import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ParallelRequestsTest
{
    private static final BpmnModelInstance MODEL =
            Bpmn.createExecutableProcess("anId").startEvent().endEvent().done();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    protected WorkflowDefinition workflowDefinition;

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Before
    public void deployModelInstance()
    {
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        workflowDefinition = workflowService.deploy()
            .bpmnModelInstance(MODEL)
            .execute();
    }

    /**
     * Smoke test for whether responses get mixed up
     */
    @Test
    public void shouldHandleParallelDeploymentAndInstantiation()
    {
        // given
        final ParallelRequests parallelRequests = ParallelRequests.prepare();

        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowsClient = client.workflows();

        final SilentFuture<WorkflowInstance> instantiationFuture =
                parallelRequests.submitRequest(
                    () ->
                    TestUtil.doRepeatedly(() ->
                        client.workflows()
                            .start()
                            .workflowDefinitionId(workflowDefinition.getId())
                            .execute())
                        .until(
                            (wfInstance) -> wfInstance != null,
                            (exception) -> !exception.getMessage().contains("(1-3)")));

        final SilentFuture<WorkflowDefinition> deploymentFuture =
                parallelRequests.submitRequest(
                    () ->
                    {
                        Thread.sleep(10);
                        return workflowsClient.deploy()
                            .bpmnModelInstance(MODEL)
                            .execute();
                    });

        // when
        parallelRequests.execute();

        // then
        assertThat(deploymentFuture.get()).isNotNull();
        assertThat(instantiationFuture.get()).isNotNull();

    }

    /**
     * Smoke test. If it fails it may not always be reproducible since the behavior
     * may depend on broker-internal processing and timing, e.g. if both requests
     * are handled in the same batch
     */
    @Test
    public void shouldHandleParallelTaskLockingAndCompletion()
    {
        // given
        final ParallelRequests parallelRequests = ParallelRequests.prepare();

        final TngpClient client = clientRule.getClient();
        final AsyncTasksClient tasksClient = client.tasks();

        final Long task1Id = tasksClient.create()
            .taskQueueId(0)
            .taskType("foo")
            .execute();

        tasksClient.pollAndLock()
            .taskQueueId(0)
            .taskType("foo")
            .lockTime(10000L)
            .maxTasks(1)
            .execute();

        final Long task2Id = tasksClient.create()
            .taskQueueId(0)
            .taskType("bar")
            .execute();

        final SilentFuture<LockedTasksBatch> lockedTasksFuture = parallelRequests.submitRequest(
            () ->
                tasksClient
                    .pollAndLock()
                    .taskQueueId(0)
                    .taskType("bar")
                    .lockTime(10000L)
                    .maxTasks(1)
                    .execute());
        final SilentFuture<Long> completionFuture = parallelRequests.submitRequest(
            () ->
                tasksClient.complete().taskQueueId(0).taskId(task1Id).execute());

        // when
        parallelRequests.execute();

        // then
        assertThat(completionFuture.get()).isEqualTo(task1Id);

        final LockedTasksBatch tasksBatch = lockedTasksFuture.get();
        assertThat(tasksBatch.getLockedTasks()).hasSize(1);
        assertThat(tasksBatch.getLockedTasks().get(0).getId()).isEqualTo(task2Id);
    }


}
