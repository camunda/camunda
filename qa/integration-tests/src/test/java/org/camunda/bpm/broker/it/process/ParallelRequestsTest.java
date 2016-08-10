package org.camunda.bpm.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.broker.it.TestUtil;
import org.camunda.bpm.broker.it.util.ParallelRequests;
import org.camunda.bpm.broker.it.util.ParallelRequests.SilentFuture;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowsClient;
import org.camunda.tngp.client.cmd.WorkflowDefinition;
import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.junit.Before;
import org.junit.Ignore;
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
    @Ignore
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


}
