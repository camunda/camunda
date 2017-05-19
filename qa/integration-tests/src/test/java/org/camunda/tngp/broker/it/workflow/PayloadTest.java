package org.camunda.tngp.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.test.util.bpmn.TngpModelInstance.wrap;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.client.task.Task;
import org.camunda.tngp.client.task.TaskHandler;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@Ignore
public class PayloadTest
{
    private static final BpmnModelInstance TWO_TASKS_WORKFLOW = wrap(Bpmn.createExecutableProcess("anId")
            .startEvent()
            .serviceTask("serviceTask1")
            .serviceTask("serviceTask2")
            .endEvent()
            .done())
        .taskAttributes("serviceTask1", "foo", 0)
        .taskAttributes("serviceTask2", "bar", 0);

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Test
    public void testPayloadModification() throws InterruptedException
    {
        // given
        final TaskTopicClient taskService = clientRule.taskTopic();
        final WorkflowTopicClient workflowsClient = clientRule.workflowTopic();

        workflowsClient.deploy()
            .bpmnModelInstance(TWO_TASKS_WORKFLOW)
            .execute();

        workflowsClient
            .create()
            .bpmnProcessId("foo")
            .payload("foo")
            .execute();

        // when
        final PayloadRecordingHandler taskHandler = new PayloadRecordingHandler();

        taskService.newTaskSubscription()
            .handler((t) ->
            {
                taskHandler.handle(t);
                t.setPayload("bar");
            })
            .taskType("foo")
            .open();

        taskService.newTaskSubscription()
            .handler(taskHandler)
            .taskType("bar")
            .open();


        // then
        TestUtil.waitUntil(() -> taskHandler.payloads.size() == 2);

        assertThat(taskHandler.payloads).hasSize(2);

        assertThat(taskHandler.payloads.get(0)).isEqualTo("foo");
        assertThat(taskHandler.payloads.get(1)).isEqualTo("foobar");
    }

    public static final class PayloadRecordingHandler implements TaskHandler
    {

        protected List<String> payloads = new ArrayList<>();

        @Override
        public void handle(Task task)
        {
            payloads.add(task.getPayload());
        }

    }

}
