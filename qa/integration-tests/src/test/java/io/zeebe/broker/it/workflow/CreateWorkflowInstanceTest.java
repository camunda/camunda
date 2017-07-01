package io.zeebe.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.broker.it.util.TopicEventRecorder.wfEvent;
import static io.zeebe.test.util.TestUtil.waitUntil;

import org.camunda.bpm.model.bpmn.Bpmn;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.WorkflowTopicClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.workflow.cmd.WorkflowInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CreateWorkflowInstanceTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public TopicEventRecorder wfEventRecorder = new TopicEventRecorder(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(wfEventRecorder);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void deployProcess()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        workflowService.deploy()
            .bpmnModelInstance(
                Bpmn.createExecutableProcess("anId")
                    .startEvent()
                    .endEvent()
                    .done())
            .execute();

        workflowService.deploy()
            .bpmnModelInstance(
                Bpmn.createExecutableProcess("anId")
                    .startEvent()
                    .endEvent()
                    .done())
            .execute();
    }

    @Test
    public void shouldCreateBpmnProcessById()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // when
        final WorkflowInstance workflowInstance =
            workflowService
                .create()
                .bpmnProcessId("anId")
                .execute();

        // then instance of latest of workflow version is created
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("anId");
        assertThat(workflowInstance.getVersion()).isEqualTo(2);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);

        waitUntil(() -> wfEventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_CREATED")));
    }

    @Test
    public void shouldCreateBpmnProcessByIdAndVersion()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();


        // when
        final WorkflowInstance workflowInstance =
            workflowService
                .create()
                .bpmnProcessId("anId")
                .version(1)
                .execute();

        // then instance is created of first workflow version
        assertThat(workflowInstance.getBpmnProcessId()).isEqualTo("anId");
        assertThat(workflowInstance.getVersion()).isEqualTo(1);
        assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);

        waitUntil(() -> wfEventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_CREATED")));
    }

    @Test
    public void shouldRejectCreateBpmnProcessByIllegalId()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // expected
        exception.expect(ClientCommandRejectedException.class);
        exception.expectMessage("Failed to create instance of workflow with BPMN process id 'illegal' and version '-1'.");

        // when
        workflowService
            .create()
            .bpmnProcessId("illegal")
            .execute();
    }

    @Test
    public void shouldThrowExceptionForCreateBpmnProcessByNullBpmnProcessId()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // expected
        exception.expect(RuntimeException.class);
        exception.expectMessage("bpmnProcessId must not be null");

        // when
        workflowService
            .create()
            .bpmnProcessId(null)
            .execute();
    }

    @Test
    public void shouldThrowExceptionForCreateBpmnProcessByEmptyBpmnProcessId()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // expected
        exception.expect(RuntimeException.class);
        exception.expectMessage("bpmnProcessId must not be empty");

        // when
        workflowService
            .create()
            .bpmnProcessId("")
            .execute();
    }

    @Test
    public void shouldThrowExceptionForCreateBpmnProcessByIllegalVersion()
    {
        final WorkflowTopicClient workflowService = clientRule.workflowTopic();

        // expected
        exception.expect(RuntimeException.class);
        exception.expectMessage("version must be greater than or equal to -1");

        // when
        workflowService
            .create()
            .bpmnProcessId("anId")
            .version(-10)
            .execute();
    }
}
