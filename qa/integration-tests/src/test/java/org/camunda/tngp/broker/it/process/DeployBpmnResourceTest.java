package org.camunda.tngp.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.Process;
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
            .bpmnModelInstance(Bpmn.createProcess().startEvent().endEvent().done())
            .execute();

        // then
        assertThat(result.isDeployed()).isFalse();
        assertThat(result.getDeployedWorkflows()).hasSize(0);
        assertThat(result.getErrorMessage()).contains("BPMN model must contain at least one executable process");
    }

    @Test
    public void shouldNotDeployTwoProcessesInsideOneDiagram()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final WorkflowTopicClient workflowService = client.workflowTopic(0);

        final BpmnModelInstance modelInstance = Bpmn.createEmptyModel();
        final Definitions definitions = modelInstance.newInstance(Definitions.class);
        definitions.setTargetNamespace(BPMN20_NS);
        modelInstance.setDefinitions(definitions);

        final Process process1 = modelInstance.newInstance(Process.class);
        definitions.addChildElement(process1);
        process1.setExecutable(true);
        process1.setId("process1");
        process1.builder().startEvent().endEvent();

        final Process process2 = modelInstance.newInstance(Process.class);
        definitions.addChildElement(process2);
        process2.setExecutable(true);
        process2.setId("process2");
        process2.builder().startEvent().endEvent();

        // when
        final DeploymentResult result = workflowService.deploy()
            .bpmnModelInstance(modelInstance)
            .execute();

        // then
        assertThat(result.isDeployed()).isFalse();
        assertThat(result.getErrorMessage()).contains("BPMN model must not contain more than one executable process");
    }

}
