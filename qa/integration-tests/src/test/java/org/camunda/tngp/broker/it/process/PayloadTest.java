package org.camunda.tngp.broker.it.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.client.AsyncTasksClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowsClient;
import org.camunda.tngp.client.cmd.WorkflowDefinition;
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
        final TngpClient client = clientRule.getClient();
        final AsyncTasksClient taskService = client.tasks();
        final WorkflowsClient workflowsClient = client.workflows();

        final WorkflowDefinition workflowDefinition = workflowsClient.deploy()
            .bpmnModelInstance(ProcessModels.TWO_TASKS_PROCESS)
            .execute();

        workflowsClient
            .start()
            .workflowDefinitionId(workflowDefinition.getId())
            .payload("foo")
            .execute();

        // when
        final PayloadRecordingHandler taskHandler = new PayloadRecordingHandler();

        taskService.newSubscription()
            .handler((t) ->
            {
                taskHandler.handle(t);
                t.setPayload("bar");
            })
            .taskType("foo")
            .open();

        taskService.newSubscription()
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
