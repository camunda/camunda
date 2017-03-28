package org.camunda.tngp.broker.protocol.clientapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CreateWorkflowInstanceTest
{

    public static final String PROP_BPMN_PROCESS_ID = "bpmnProcessId";
    public static final String PROP_EVENT = "eventType";
    public static final String PROP_BPMN_XML = "bpmnXml";
    public static final String PROP_VERSION = "version";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule("tngp.unit-test.cfg.toml");

    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldRejectWorkflowInstanceCreation()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .topicId(0)
                .eventTypeWorkflow()
                .command()
                    .put(PROP_EVENT, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_BPMN_PROCESS_ID, "process")
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.topicId()).isEqualTo(0L);
        assertThat(resp.getEvent())
            .containsEntry(PROP_EVENT, "WORKFLOW_INSTANCE_REJECTED")
            .containsEntry(PROP_BPMN_PROCESS_ID, "process");

    }

    @Test
    public void shouldCreateWorkflowInstance()
    {
        // given
        apiRule.topic(0).deploy(Bpmn.createExecutableProcess("process")
                .startEvent()
                .endEvent()
                .done());

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .topicId(0)
                .eventTypeWorkflow()
                .command()
                    .put(PROP_EVENT, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_BPMN_PROCESS_ID, "process")
                .done()
                .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.topicId()).isEqualTo(0L);
        assertThat(resp.getEvent())
            .containsEntry(PROP_EVENT, "WORKFLOW_INSTANCE_CREATED")
            .containsEntry(PROP_BPMN_PROCESS_ID, "process")
            .containsEntry("workflowInstanceKey", resp.key())
            .containsEntry(PROP_VERSION, 1);
    }

    @Test
    public void shouldCreateLatestVersionOfWorkflowInstance()
    {
        // given
        apiRule.topic(0).deploy(Bpmn.createExecutableProcess("process")
                .startEvent("foo")
                .endEvent()
                .done());

        apiRule.topic(0).deploy(Bpmn.createExecutableProcess("process")
                .startEvent("bar")
                .endEvent()
                .done());

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .topicId(0)
                .eventTypeWorkflow()
                .command()
                    .put(PROP_EVENT, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_BPMN_PROCESS_ID, "process")
                .done()
                .sendAndAwait();

        // then
        final SubscribedEvent event = apiRule.topic(0).receiveSingleEvent(workflowInstanceEvents("EVENT_OCCURRED"));

        assertThat(event.event())
            .containsEntry(PROP_BPMN_PROCESS_ID, "process")
            .containsEntry("workflowInstanceKey", resp.key())
            .containsEntry("activityId", "bar")
            .containsEntry(PROP_VERSION, 2);
    }


    @Test
    public void shouldCreatePreviousVersionOfWorkflowInstance()
    {
        // given
        apiRule.topic(0).deploy(Bpmn.createExecutableProcess("process")
                .startEvent("foo")
                .endEvent()
                .done());

        apiRule.topic(0).deploy(Bpmn.createExecutableProcess("process")
                .startEvent("bar")
                .endEvent()
                .done());

        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
                .topicId(0)
                .eventTypeWorkflow()
                .command()
                    .put(PROP_EVENT, "CREATE_WORKFLOW_INSTANCE")
                    .put(PROP_BPMN_PROCESS_ID, "process")
                    .put(PROP_VERSION, 1)
                .done()
                .sendAndAwait();

        // then
        final SubscribedEvent event = apiRule.topic(0).receiveSingleEvent(workflowInstanceEvents("EVENT_OCCURRED"));

        assertThat(event.event())
            .containsEntry(PROP_BPMN_PROCESS_ID, "process")
            .containsEntry("workflowInstanceKey", resp.key())
            .containsEntry("activityId", "foo")
            .containsEntry(PROP_VERSION, 1);
    }

}
