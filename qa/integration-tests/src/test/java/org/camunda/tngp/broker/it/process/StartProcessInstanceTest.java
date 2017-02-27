package org.camunda.tngp.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.client.WorkflowsClient;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.WorkflowDefinition;
import org.camunda.tngp.client.cmd.WorkflowInstance;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

@Ignore
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

    protected WorkflowDefinition process;

    @Before
    public void deployProcess()
    {
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        process = workflowService.deploy()
            .bpmnModelInstance(
                    Bpmn.createExecutableProcess("anId")
                        .startEvent()
                        .endEvent()
                        .done())
            .execute();
    }

    @Test
    public void shouldStartProcessById()
    {
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        // when
        final WorkflowInstance processInstance = TestUtil.doRepeatedly(() ->
            workflowService
                .start()
                .workflowDefinitionId(process.getId())
                .execute())
            .until(
                (wfInstance) -> wfInstance != null,
                (exception) -> !exception.getMessage().contains("(1-3)"));

        assertThat(processInstance.getId()).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void shouldStartProcessByKey() throws InterruptedException
    {
        final TngpClient client = clientRule.getClient();
        final WorkflowsClient workflowService = client.workflows();

        // when
        final WorkflowInstance processInstance = TestUtil.doRepeatedly(() ->
            workflowService
                .start()
                .workflowDefinitionKey("anId")
                .execute())
            .until(
                (wfInstance) -> wfInstance != null,
                (exception) -> !exception.getMessage().contains("(1-3)"));

        assertThat(processInstance.getId()).isGreaterThanOrEqualTo(0);
    }
}
