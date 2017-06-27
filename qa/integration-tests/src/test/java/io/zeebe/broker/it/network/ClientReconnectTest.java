package io.zeebe.broker.it.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.test.util.TestUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class ClientReconnectTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public Timeout testTimeout = Timeout.seconds(30);

    @Test
    public void shouldTransparentlyReconnectOnUnexpectedConnectionLoss()
    {
        // given
        final long initialTaskKey = createTask();

        clientRule.interruptBrokerConnections();

        // when
        final long newTaskKey = TestUtil.doRepeatedly(() -> createTask())
                .until((key) -> key != null);

        // then
        assertThat(newTaskKey).isNotEqualTo(initialTaskKey);
    }

    protected long createTask()
    {
        final long taskKey = clientRule.taskTopic().create()
            .taskType("foo")
            .addHeader("k1", "a")
            .addHeader("k2", "b")
            .payload("{ \"payload\" : 123 }")
            .execute();

        return taskKey;
    }
}
