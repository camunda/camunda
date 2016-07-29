package org.camunda.bpm.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.*;

import java.nio.charset.StandardCharsets;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.client.WorkflowsClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.BrokerRequestException;
import org.camunda.tngp.client.cmd.WorkflowDefinition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class DeployBpmnResourceTest
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
        final WorkflowsClient workflowService = client.workflows();

        final WorkflowDefinition wfDefinition = workflowService.deploy()
            .bpmnModelInstance(Bpmn.createExecutableProcess("anId").startEvent().done())
            .execute();

        assertThat(wfDefinition.getId()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldNotDeployUnparsableModel()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        // then
        exception.expect(BrokerRequestException.class);
        exception.expectMessage("Failed request (1-1): Cannot deploy Bpmn Resource");
        exception.expect(BrokerRequestExceptionMatcher.brokerException(1, 1));

        // when
        workflowService.deploy()
            .resourceBytes("Foooo".getBytes(StandardCharsets.UTF_8))
            .execute();
    }

    @Test
    public void shouldNotDeployInvalidModel()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        // then
        exception.expect(BrokerRequestException.class);
        exception.expectMessage(containsString("ERROR 201"));
        exception.expect(BrokerRequestExceptionMatcher.brokerException(1, 1));

        // when
        workflowService.deploy()
            .bpmnModelInstance(Bpmn.createExecutableProcess().done()) // does not have a start event
            .execute();
    }

}
