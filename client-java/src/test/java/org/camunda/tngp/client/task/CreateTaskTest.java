package org.camunda.tngp.client.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.util.ClientRule;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.broker.protocol.brokerapi.StubBrokerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CreateTaskTest
{

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    protected TngpClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @Test
    public void shouldCreateTask()
    {
        // given
        brokerRule.onExecuteCommandRequest(ecr -> ecr.eventType() == EventType.TASK_EVENT &&
                "CREATE".equals(ecr.getCommand().get("eventType")))
            .respondWith()
            .topicId(0)
            .key(123)
            .event()
              .allOf((r) -> r.getCommand())
              .put("eventType", "CREATED")
              .put("headers", new HashMap<>())
              .put("payload", new byte[0])
              .done()
            .register();

        // when
        final Long taskKey = client.taskTopic(0)
            .create()
            .taskType("foo")
            .retries(3)
            .execute();

        // then
        assertThat(taskKey).isEqualTo(123L);
    }
}
