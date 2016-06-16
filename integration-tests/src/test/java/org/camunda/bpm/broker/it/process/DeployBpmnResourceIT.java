package org.camunda.bpm.broker.it.process;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowService;
import org.camunda.tngp.client.cmd.DeployedWorkflowType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.assertj.core.api.Assertions.*;

public class DeployBpmnResourceIT
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
        final TngpClient client = clientRule.getClient();
        final WorkflowService workflowService = client.getWorkflowService();

        final DeployedWorkflowType wfType = workflowService.deployBpmnResource()
            .bpmnModelInstance(Bpmn.createExecutableProcess("anId").startEvent().done())
            .execute();

        assertThat(wfType.getWorkflowTypeId()).isGreaterThanOrEqualTo(0);
    }

}
