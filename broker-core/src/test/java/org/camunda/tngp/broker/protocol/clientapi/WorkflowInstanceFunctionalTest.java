package org.camunda.tngp.broker.protocol.clientapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient.workflowInstanceEvents;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.SubscribedEvent;
import org.camunda.tngp.test.broker.protocol.clientapi.TestTopicClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowInstanceFunctionalTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule("tngp.unit-test.cfg.toml");
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient testTopicClient;

    @Before
    public void init()
    {
        testTopicClient = apiRule.topic(0);
    }

    @Test
    public void shouldStartWorkflowInstanceAtNoneStartEvent()
    {
        // given
        testTopicClient.deploy(Bpmn.createExecutableProcess("process")
                .startEvent("foo")
                .endEvent()
                .done());

        // when
        final long workflowInstanceKey = testTopicClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent event = testTopicClient.receiveSingleEvent(workflowInstanceEvents("EVENT_OCCURRED"));

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("version", 1)
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "foo");
    }

    @Test
    public void shouldTakeSequenceFlowFromStartEvent()
    {
        // given
        testTopicClient.deploy(Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .sequenceFlowId("foo")
                    .endEvent()
                    .done());

        // when
        final long workflowInstanceKey = testTopicClient.createWorkflowInstance("process");

        // then
        final SubscribedEvent event = testTopicClient.receiveSingleEvent(workflowInstanceEvents("SEQUENCE_FLOW_TAKEN"));

        assertThat(event.longKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
        assertThat(event.event())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("version", 1)
            .containsEntry("workflowInstanceKey", workflowInstanceKey)
            .containsEntry("activityId", "foo");
    }

}
