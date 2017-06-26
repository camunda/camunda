package org.camunda.tngp.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.IncidentEvent;
import org.camunda.tngp.client.event.IncidentEventHandler;
import org.camunda.tngp.client.event.TopicEventType;
import org.camunda.tngp.client.workflow.cmd.WorkflowInstance;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class IncidentTopicSubscriptionTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    protected TngpClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();

        final BpmnModelInstance workflow =
                wrap(Bpmn.createExecutableProcess("process")
                     .startEvent("start")
                     .serviceTask("task")
                     .endEvent("end")
                     .done())
                .taskDefinition("task", "test", 3)
                    .ioMapping("task")
                        .input("$.foo", "$.foo")
                        .done();

        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(workflow)
            .execute();
    }

    @Test
    public void shouldReceiveWorkflowIncidentEvents()
    {
        // given
        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        final RecordingIncidentEventHandler handler = new RecordingIncidentEventHandler();

        // when
        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .incidentEventHandler(handler)
            .name("test")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 2);

        final IncidentEvent event = handler.getEvent(1);
        assertThat(event.getEventType()).isEqualTo("CREATED");
        assertThat(event.getErrorType()).isEqualTo("IO_MAPPING_ERROR");
        assertThat(event.getErrorMessage()).isEqualTo("No data found for query $.foo.");
        assertThat(event.getBpmnProcessId()).isEqualTo("process");
        assertThat(event.getWorkflowInstanceKey()).isEqualTo(workflowInstance.getWorkflowInstanceKey());
        assertThat(event.getActivityId()).isEqualTo("task");
        assertThat(event.getActivityInstanceKey()).isGreaterThan(0);
        assertThat(event.getTaskKey()).isNull();
    }

    @Test
    public void shouldReceiveTaskIncidentEvents()
    {
        // given
        final Long taskKey = clientRule.taskTopic().create()
            .taskType("test")
            .execute();

        clientRule.taskTopic().newTaskSubscription()
            .lockTime(Duration.ofMinutes(5))
            .lockOwner("test")
            .taskType("test")
            .handler(t ->
            {
                throw new RuntimeException("expected failure");
            })
            .open();

        final RecordingIncidentEventHandler handler = new RecordingIncidentEventHandler();

        // when
        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .incidentEventHandler(handler)
            .name("test")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEvents() >= 2);

        final IncidentEvent event = handler.getEvent(1);
        assertThat(event.getEventType()).isEqualTo("CREATED");
        assertThat(event.getErrorType()).isEqualTo("TASK_NO_RETRIES");
        assertThat(event.getErrorMessage()).isEqualTo("No more retries left.");
        assertThat(event.getBpmnProcessId()).isNull();
        assertThat(event.getWorkflowInstanceKey()).isNull();
        assertThat(event.getActivityId()).isNull();
        assertThat(event.getActivityInstanceKey()).isNull();
        assertThat(event.getTaskKey()).isEqualTo(taskKey);
    }

    @Test
    public void shouldInvokeDefaultHandler() throws IOException
    {
        // given
        clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        final RecordingEventHandler handler = new RecordingEventHandler();

        // when no POJO handler is registered
        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(handler)
            .name("sub-2")
            .open();

        // then
        TestUtil.waitUntil(() -> handler.numRecordedEventsOfType(TopicEventType.INCIDENT) >= 2);
    }

    protected static class RecordingIncidentEventHandler implements IncidentEventHandler
    {
        protected List<EventMetadata> metadata = new ArrayList<>();
        protected List<IncidentEvent> events = new ArrayList<>();

        @Override
        public void handle(EventMetadata metadata, IncidentEvent event) throws Exception
        {
            this.metadata.add(metadata);
            this.events.add(event);
        }

        public EventMetadata getMetadata(int index)
        {
            return metadata.get(index);
        }

        public IncidentEvent getEvent(int index)
        {
            return events.get(index);
        }

        public int numRecordedEvents()
        {
            return events.size();
        }

    }
}
