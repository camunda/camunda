package org.camunda.tngp.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.util.ParallelRequests;
import org.camunda.tngp.broker.it.util.ParallelRequests.SilentFuture;
import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.client.workflow.cmd.DeploymentResult;
import org.camunda.tngp.client.workflow.cmd.WorkflowInstance;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@Ignore
public class ParallelRequestsTest
{
    private static final BpmnModelInstance MODEL =
            Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Before
    public void deployModelInstance()
    {
        final TngpClient client = clientRule.getClient();
        final WorkflowTopicClient workflowService = client.workflowTopic(0);

        workflowService.deploy()
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
        final WorkflowTopicClient workflowsClient = client.workflowTopic(0);

        final SilentFuture<WorkflowInstance> instantiationFuture =
                parallelRequests.submitRequest(
                    () ->
                    TestUtil.doRepeatedly(() ->
                        client.workflowTopic(0)
                            .create()
                            .bpmnProcessId("foo")
                            .execute())
                        .until(
                            (wfInstance) -> wfInstance != null,
                            (exception) -> !exception.getMessage().contains("(1-3)")));

        final SilentFuture<DeploymentResult> deploymentFuture =
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
        final TaskTopicClient tasksClient = client.taskTopic(0);

        final Long task1Id = tasksClient.create()
            .taskType("foo")
            .execute();

        tasksClient.pollAndLock()
            .taskType("foo")
            .lockTime(10000L)
            .maxTasks(1)
            .execute();

        final Long task2Id = tasksClient.create()
            .taskType("bar")
            .execute();

        final SilentFuture<LockedTasksBatch> lockedTasksFuture = parallelRequests.submitRequest(
            () ->
                tasksClient
                    .pollAndLock()
                    .taskType("bar")
                    .lockTime(10000L)
                    .maxTasks(1)
                    .execute());
        final SilentFuture<Long> completionFuture = parallelRequests.submitRequest(
            () ->
                tasksClient.complete().taskKey(task1Id).execute());

        // when
        parallelRequests.execute();

        // then
        assertThat(completionFuture.get()).isEqualTo(task1Id);

        final LockedTasksBatch tasksBatch = lockedTasksFuture.get();
        assertThat(tasksBatch.getLockedTasks()).hasSize(1);
        assertThat(tasksBatch.getLockedTasks().get(0).getId()).isEqualTo(task2Id);
    }


}
