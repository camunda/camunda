package org.camunda.tngp.broker.protocol.clientapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.protocol.clientapi.EventType;
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

        final Map<String, Object> event = resp.getEvent();
        assertThat(event).containsEntry(PROP_EVENT, "WORKFLOW_INSTANCE_REJECTED");
        assertThat(event).containsEntry(PROP_BPMN_PROCESS_ID, "process");

    }

    @Test
    public void shouldCreateWorkflowInstance()
    {
        // given
        deploy(Bpmn.createExecutableProcess("process")
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

        final Map<String, Object> event = resp.getEvent();
        assertThat(event).containsEntry(PROP_EVENT, "WORKFLOW_INSTANCE_CREATED");
        assertThat(event).containsEntry(PROP_BPMN_PROCESS_ID, "process");
        assertThat(event).containsEntry("workflowInstanceKey", resp.key());
        assertThat(event).containsEntry(PROP_VERSION, 1);
    }

    @Test
    public void shouldStartWorkflowInstance()
    {
        // given
        deploy(Bpmn.createExecutableProcess("process")
                .startEvent("ID")
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
        final List<SubscribedEvent> workflowEvents = apiRule.openSubscription(0)
                .subscribedEvents()
                .filter(p -> p.eventType() == EventType.WORKFLOW_EVENT && p.event().get("eventType").equals("EVENT_OCCURRED"))
                .limit(1)
                .collect(Collectors.toList());

        assertThat(workflowEvents).isNotEmpty();

        final Map<String, Object> event = workflowEvents.get(0).event();
        assertThat(event).containsEntry(PROP_BPMN_PROCESS_ID, "process");
        assertThat(event).containsEntry("workflowInstanceKey", resp.key());
        assertThat(event).containsEntry("activityId", "ID");
        assertThat(event).containsEntry(PROP_VERSION, 1);
    }


    @Test
    public void shouldStartLatestWorkflowInstance()
    {
        // given
        deploy(Bpmn.createExecutableProcess("process")
                .startEvent("foo")
                .endEvent()
                .done());

        deploy(Bpmn.createExecutableProcess("process")
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
        final List<SubscribedEvent> workflowEvents = apiRule.openSubscription(0)
                .subscribedEvents()
                .filter(p -> p.eventType() == EventType.WORKFLOW_EVENT && p.event().get("eventType").equals("EVENT_OCCURRED"))
                .limit(1)
                .collect(Collectors.toList());

        assertThat(workflowEvents).isNotEmpty();

        final Map<String, Object> event = workflowEvents.get(0).event();
        assertThat(event).containsEntry(PROP_BPMN_PROCESS_ID, "process");
        assertThat(event).containsEntry("workflowInstanceKey", resp.key());
        assertThat(event).containsEntry("activityId", "bar");
        assertThat(event).containsEntry(PROP_VERSION, 2);
    }


    @Test
    public void shouldStartFirstWorkflowInstance()
    {
        // given
        deploy(Bpmn.createExecutableProcess("process")
                .startEvent("foo")
                .endEvent()
                .done());

        deploy(Bpmn.createExecutableProcess("process")
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
        final List<SubscribedEvent> workflowEvents = apiRule.openSubscription(0)
                .subscribedEvents()
                .filter(p -> p.eventType() == EventType.WORKFLOW_EVENT && p.event().get("eventType").equals("EVENT_OCCURRED"))
                .limit(1)
                .collect(Collectors.toList());

        assertThat(workflowEvents).isNotEmpty();

        final Map<String, Object> event = workflowEvents.get(0).event();
        assertThat(event).containsEntry(PROP_BPMN_PROCESS_ID, "process");
        assertThat(event).containsEntry("workflowInstanceKey", resp.key());
        assertThat(event).containsEntry("activityId", "foo");
        assertThat(event).containsEntry(PROP_VERSION, 1);
    }

    private void deploy(final BpmnModelInstance modelInstance)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
                .topicId(0)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put(PROP_EVENT, "CREATE_DEPLOYMENT")
                    .put(PROP_BPMN_XML, Bpmn.convertToString(modelInstance))
                .done()
                .sendAndAwait();

        assertThat(response.getEvent().get("eventType")).isEqualTo("DEPLOYMENT_CREATED");
    }

}
