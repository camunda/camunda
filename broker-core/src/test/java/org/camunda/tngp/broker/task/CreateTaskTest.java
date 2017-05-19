package org.camunda.tngp.broker.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.logstreams.log.LogStream.DEFAULT_TOPIC_NAME;

import java.util.Map;

import org.camunda.tngp.broker.test.EmbeddedBrokerRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;


public class CreateTaskTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldCreateTask()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(0)
            .eventTypeTask()
            .command()
                .put("eventType", "CREATE")
                .put("type", "theTaskType")
                .done()
            .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.getTopicName()).isEqualTo(DEFAULT_TOPIC_NAME);
        assertThat(resp.partitionId()).isEqualTo(0);

        final Map<String, Object> event = resp.getEvent();
        assertThat(event).containsEntry("eventType", "CREATED");
        assertThat(event).containsEntry("type", "theTaskType");
    }
}
