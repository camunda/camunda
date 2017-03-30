package org.camunda.tngp.client.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.util.ClientRule;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.test.broker.protocol.brokerapi.StubBrokerRule;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TaskSubscriptionTest
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
    public void shouldCloseSubscriptionOnChannelClose()
    {
        // given
        brokerRule.onControlMessageRequest((r) -> r.messageType() == ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .put("id", 123L)
                .done()
            .register();

        final TaskSubscription subscription = client.taskTopic(0).newTaskSubscription()
            .handler((t) -> t.complete())
            .lockOwner(0)
            .lockTime(1000L)
            .taskFetchSize(5)
            .taskType("foo")
            .open();

        // when
        brokerRule.closeServerSocketBinding();

        // then
        TestUtil.waitUntil(() -> subscription.isClosed());
        assertThat(subscription.isClosed()).isTrue();
    }

}
