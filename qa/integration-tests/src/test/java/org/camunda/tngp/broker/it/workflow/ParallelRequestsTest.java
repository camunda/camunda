package org.camunda.tngp.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.util.ParallelRequests;
import org.camunda.tngp.broker.it.util.ParallelRequests.SilentFuture;
import org.camunda.tngp.client.WorkflowTopicClient;
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
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

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

        final WorkflowTopicClient workflowsClient = clientRule.workflowTopic();

        final SilentFuture<WorkflowInstance> instantiationFuture =
                parallelRequests.submitRequest(
                    () ->
                    TestUtil.doRepeatedly(() ->
                        clientRule.workflowTopic()
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

}
