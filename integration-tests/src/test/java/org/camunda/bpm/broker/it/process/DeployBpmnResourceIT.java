package org.camunda.bpm.broker.it.process;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.ProcessService;
import org.camunda.tngp.client.cmd.DeployedWorkflowType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.internal.runners.statements.ExpectException;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;

public class DeployBpmnResourceIT
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldDeployModelInstance()
    {
        final TngpClient client = clientRule.getClient();
        final ProcessService workflowService = client.processes();

        final DeployedWorkflowType wfType = workflowService.deploy()
            .bpmnModelInstance(Bpmn.createExecutableProcess("anId").startEvent().done())
            .execute();

        assertThat(wfType.getWorkflowTypeId()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldNotDeployInvalidModel()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final ProcessService workflowService = client.processes();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Cannot deploy Bpmn Resource");

        // when
        workflowService.deploy()
            .resourceBytes("Foooo".getBytes(StandardCharsets.UTF_8))
            .execute();
    }

}
