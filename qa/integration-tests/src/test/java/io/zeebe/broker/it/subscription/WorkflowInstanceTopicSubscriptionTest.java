package io.zeebe.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.EventMetadata;
import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.event.WorkflowInstanceEvent;
import io.zeebe.client.event.WorkflowInstanceEventHandler;
import io.zeebe.client.workflow.cmd.WorkflowInstance;
import io.zeebe.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowInstanceTopicSubscriptionTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    protected ZeebeClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();

        final BpmnModelInstance workflow = Bpmn.createExecutableProcess("process")
                .startEvent("a")
                .endEvent("b")
                .done();

        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(workflow)
            .execute();
    }

    @Test
    public void shouldReceiveWorkflowInstanceEvents()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .payload("{\"foo\":123}")
            .execute();

        final RecordingWorkflowEventHandler handler = new RecordingWorkflowEventHandler();

        // when
        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .workflowInstanceEventHandler(handler)
            .name("test")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 3);

        final WorkflowInstanceEvent event = handler.getEvent(2);
        assertThat(event.getEventType()).isEqualTo("START_EVENT_OCCURRED");
        assertThat(event.getBpmnProcessId()).isEqualTo("process");
        assertThat(event.getVersion()).isEqualTo(1);
        assertThat(event.getWorkflowInstanceKey()).isEqualTo(workflowInstance.getWorkflowInstanceKey());
        assertThat(event.getActivityId()).isEqualTo("a");
        assertThat(event.getPayload()).isEqualTo("{\"foo\":123}");
    }

    @Test
    public void shouldInvokeDefaultHandler() throws IOException
    {
        // given
        clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .payload("{\"foo\":123}")
            .execute();

        final RecordingEventHandler handler = new RecordingEventHandler();

        // when no POJO handler is registered
        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(handler)
            .name("sub-2")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEventsOfType(TopicEventType.WORKFLOW_INSTANCE) >= 3);
    }

    protected static class RecordingWorkflowEventHandler implements WorkflowInstanceEventHandler
    {
        protected List<EventMetadata> metadata = new ArrayList<>();
        protected List<WorkflowInstanceEvent> events = new ArrayList<>();

        @Override
        public void handle(EventMetadata metadata, WorkflowInstanceEvent event) throws Exception
        {
            this.metadata.add(metadata);
            this.events.add(event);
        }

        public EventMetadata getMetadata(int index)
        {
            return metadata.get(index);
        }

        public WorkflowInstanceEvent getEvent(int index)
        {
            return events.get(index);
        }

        public int numRecordedEvents()
        {
            return events.size();
        }

    }
}
