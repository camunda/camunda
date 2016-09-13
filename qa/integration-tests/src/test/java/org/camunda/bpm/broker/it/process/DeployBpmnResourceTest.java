package org.camunda.bpm.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static org.hamcrest.CoreMatchers.*;

import java.nio.charset.StandardCharsets;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.Process;
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
        exception.expectMessage("Failed request (1-2): Cannot deploy Bpmn Resource");
        exception.expect(BrokerRequestExceptionMatcher.brokerException(1, 2));

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
        exception.expect(BrokerRequestExceptionMatcher.brokerException(1, 2));

        // when
        workflowService.deploy()
            .bpmnModelInstance(Bpmn.createExecutableProcess().done()) // does not have a start event
            .execute();
    }

    @Test
    public void shouldNotDeployNonExecutableModel()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        // then
        exception.expect(BrokerRequestException.class);
        exception.expectMessage(containsString("ERROR 203"));
        exception.expect(BrokerRequestExceptionMatcher.brokerException(1, 2));

        // when
        workflowService.deploy()
            .bpmnModelInstance(Bpmn.createProcess().startEvent().endEvent().done())
            .execute();
    }

    @Test
    public void shouldNotDeployTwoProcesses()
    {
        // given
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        final BpmnModelInstance modelInstance = Bpmn.createEmptyModel();
        final Definitions definitions = modelInstance.newInstance(Definitions.class);
        definitions.setTargetNamespace(BPMN20_NS);
        modelInstance.setDefinitions(definitions);

        final Process process1 = modelInstance.newInstance(Process.class);
        definitions.addChildElement(process1);
        process1.setExecutable(true);
        process1.builder().startEvent().endEvent();

        final Process process2 = modelInstance.newInstance(Process.class);
        definitions.addChildElement(process2);
        process2.setExecutable(true);
        process2.builder().startEvent().endEvent();

        // then
        exception.expect(BrokerRequestException.class);
        exception.expectMessage(containsString("ERROR 204"));
        exception.expect(BrokerRequestExceptionMatcher.brokerException(1, 2));

        // when
        workflowService.deploy()
            .bpmnModelInstance(modelInstance)
            .execute();
    }

}
