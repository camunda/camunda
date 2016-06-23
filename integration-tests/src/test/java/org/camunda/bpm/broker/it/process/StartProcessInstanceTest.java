package org.camunda.bpm.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.client.ProcessService;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.DeployedWorkflowType;
import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class StartProcessInstanceTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected DeployedWorkflowType process;

    @Before
    public void deployProcess()
    {
        final TngpClient client = clientRule.getClient();
        final ProcessService workflowService = client.processes();

        process = workflowService.deploy()
            .bpmnModelInstance(Bpmn.createExecutableProcess("anId").startEvent().done())
            .execute();
    }

    @Test
    public void shouldStartProcessById() throws InterruptedException
    {
        final TngpClient client = clientRule.getClient();
        final ProcessService workflowService = client.processes();

        // when
        final WorkflowInstance processInstance = workflowService.start()
            .workflowTypeId(process.getWorkflowTypeId())
            .execute();

        assertThat(processInstance.getId()).isGreaterThanOrEqualTo(0);
    }
}
