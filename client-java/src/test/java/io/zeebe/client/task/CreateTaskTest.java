package io.zeebe.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_PARTITION_ID;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;

import java.util.HashMap;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
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

    protected ZeebeClient client;

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
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .key(123)
            .event()
              .allOf((r) -> r.getCommand())
              .put("eventType", "CREATED")
              .put("headers", new HashMap<>())
              .put("payload", new byte[0])
              .done()
            .register();

        // when
        final Long taskKey = clientRule.taskTopic()
            .create()
            .taskType("foo")
            .retries(3)
            .execute();

        // then
        assertThat(taskKey).isEqualTo(123L);
    }
}
