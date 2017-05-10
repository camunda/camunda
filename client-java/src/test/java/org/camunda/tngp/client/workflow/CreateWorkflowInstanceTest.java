package org.camunda.tngp.client.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.util.StringUtil.getBytes;

import java.io.ByteArrayInputStream;

import org.camunda.tngp.client.ClientCommandRejectedException;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.util.ClientRule;
import org.camunda.tngp.client.workflow.cmd.WorkflowInstance;
import org.camunda.tngp.test.broker.protocol.brokerapi.StubBrokerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;


public class CreateWorkflowInstanceTest
{
    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    protected TngpClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @Test
    public void shouldCreateWorkflowInstance()
    {
        // given
        brokerRule.onWorkflowRequestRespondWith(1L)
                .put("eventType", "WORKFLOW_INSTANCE_CREATED")
                .put("version", 1)
                .put("workflowInstanceKey", 1)
                .done()
                .register();

        // when
        final WorkflowInstance workflowInstance = clientRule.workflowTopic()
                .create()
                .bpmnProcessId("foo")
                .execute();

        // then
        assertThat(workflowInstance).isNotNull();
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("foo");
        assertThat(workflowInstance.getVersion()).isEqualTo(1);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(1);
    }

    @Test
    public void shouldCreateWorkflowInstanceWithPayload()
    {
        // given
        final byte[] payload = getBytes("{ \"bar\" : 4 }");

        brokerRule.onWorkflowRequestRespondWith(1L)
            .put("eventType", "WORKFLOW_INSTANCE_CREATED")
            .put("version", 1)
            .put("workflowInstanceKey", 1)
            .put("payload", payload)
            .done()
            .register();

        // when
        final WorkflowInstance workflowInstance = clientRule.workflowTopic()
            .create()
            .bpmnProcessId("foo")
            .payload(new ByteArrayInputStream(payload))
            .execute();

        // then
        assertThat(workflowInstance).isNotNull();
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("foo");
        assertThat(workflowInstance.getVersion()).isEqualTo(1);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(1);
        assertThat(workflowInstance.getPayload()).isEqualTo(payload);
    }

    @Test
    public void shouldNotCreateWorkflowInstanceForMissingBpmnProcessId()
    {
        // expect exception
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("bpmnProcessId must not be null");

        // when create command is executed without BPMN process id
        clientRule.workflowTopic()
                .create()
                .execute();
    }

    @Test
    public void shouldRejectCreateWorkflowInstance()
    {
        // given
        brokerRule.onWorkflowRequestRespondWith(1L)
                .put("eventType", "WORKFLOW_INSTANCE_REJECTED")
                .put("bpmnProcessId", "foo")
                .put("version", 1)
                .done()
                .register();


        // expect exception
        expectedException.expect(ClientCommandRejectedException.class);
        expectedException.expectMessage("Failed to create instance of workflow with BPMN process id 'foo' and version '1'.");

        // when
        clientRule.workflowTopic()
            .create()
            .bpmnProcessId("foo")
            .execute();
    }

    @Test
    public void shouldCreateWorkflowInstanceWithVersion()
    {
        // given
        brokerRule.onWorkflowRequestRespondWith(1L)
                .put("eventType", "WORKFLOW_INSTANCE_CREATED")
                .put("workflowInstanceKey", 1)
                .done()
                .register();

        // when
        final WorkflowInstance workflowInstance = clientRule.workflowTopic()
                .create()
                .bpmnProcessId("foo")
                .version(2)
                .execute();

        // then
        assertThat(workflowInstance).isNotNull();
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("foo");
        assertThat(workflowInstance.getVersion()).isEqualTo(2);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(1);
    }
}
