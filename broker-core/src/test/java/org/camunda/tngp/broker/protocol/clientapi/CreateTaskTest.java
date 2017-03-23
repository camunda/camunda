package org.camunda.tngp.broker.protocol.clientapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule;
import org.camunda.tngp.test.broker.protocol.clientapi.ExecuteCommandResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CreateTaskTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule("tngp.unit-test.cfg.toml");

    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldCreateTask()
    {
        // when
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
            .topicId(0)
            .eventTypeTask()
            .command()
                .put("eventType", "CREATE")
                .put("type", "theTaskType")
                .done()
            .sendAndAwait();

        // then
        assertThat(resp.key()).isGreaterThanOrEqualTo(0L);
        assertThat(resp.topicId()).isEqualTo(0L);

        final Map<String, Object> event = resp.getEvent();
        assertThat(event).containsEntry("eventType", "CREATED");
        assertThat(event).containsEntry("type", "theTaskType");
    }
}
