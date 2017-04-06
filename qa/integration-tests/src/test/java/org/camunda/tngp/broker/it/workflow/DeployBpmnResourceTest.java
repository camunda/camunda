package org.camunda.tngp.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.client.workflow.cmd.DeploymentResult;
import org.camunda.tngp.client.workflow.cmd.WorkflowDefinition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class DeployBpmnResourceTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Test
    public void shouldDeployModelInstance()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final WorkflowTopicClient workflowService = client.workflowTopic(0);

        // when
        final DeploymentResult result = workflowService.deploy()
            .bpmnModelInstance(Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .endEvent()
                    .done())
            .execute();

        // then
        assertThat(result.isDeployed()).isTrue();
        assertThat(result.getKey()).isGreaterThan(0);
        assertThat(result.getDeployedWorkflows()).hasSize(1);

        final WorkflowDefinition deployedWorkflow = result.getDeployedWorkflows().get(0);
        assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo("process");
        assertThat(deployedWorkflow.getVersion()).isEqualTo(1);
    }

    @Test
    public void shouldNotDeployUnparsableModel()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final WorkflowTopicClient workflowService = client.workflowTopic(0);

        // when
        final DeploymentResult result = workflowService.deploy()
                .resourceString("Foooo")
                .execute();

        // then
        assertThat(result.isDeployed()).isFalse();
        assertThat(result.getDeployedWorkflows()).hasSize(0);
        assertThat(result.getErrorMessage()).contains("Failed to read BPMN model");
    }

    @Test
    public void shouldNotDeployInvalidModel()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final WorkflowTopicClient workflowService = client.workflowTopic(0);

        // when
        final DeploymentResult result = workflowService.deploy()
            .bpmnModelInstance(Bpmn.createExecutableProcess("no-start-event").done()) // does not have a start event
            .execute();

        // then
        assertThat(result.isDeployed()).isFalse();
        assertThat(result.getDeployedWorkflows()).hasSize(0);
        assertThat(result.getErrorMessage()).contains("no-start-event").contains("The process must contain at least one none start event.");
    }

    @Test
    public void shouldNotDeployNonExecutableModel()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final WorkflowTopicClient workflowService = client.workflowTopic(0);

        // when
        final DeploymentResult result = workflowService.deploy()
            .bpmnModelInstance(Bpmn.createProcess()
                    .startEvent()
                    .endEvent()
                    .done())
            .execute();

        // then
        assertThat(result.isDeployed()).isFalse();
        assertThat(result.getDeployedWorkflows()).hasSize(0);
        assertThat(result.getErrorMessage()).contains("BPMN model must contain at least one executable process");
    }

}
