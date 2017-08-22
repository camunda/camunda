package io.zeebe.client.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.Event;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;

public class CreateTopicTest
{

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected ZeebeClient client;

    @Test
    public void shouldCreateTopic()
    {
        // given
        brokerRule.onExecuteCommandRequest(Protocol.SYSTEM_TOPIC, Protocol.SYSTEM_PARTITION, EventType.TOPIC_EVENT, "CREATE")
            .respondWith()
            .key(123)
            .position(456)
            .event()
              .allOf((r) -> r.getCommand())
              .put("state", "CREATED")
              .done()
            .register();

        // when
        final Event responseEvent = clientRule.topics().create("newTopic", 14).execute();

        // then
        final ExecuteCommandRequest request = brokerRule.getReceivedCommandRequests().get(0);
        assertThat(request.eventType()).isEqualTo(EventType.TOPIC_EVENT);
        assertThat(request.topicName()).isEqualTo(Protocol.SYSTEM_TOPIC);
        assertThat(request.partitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
        assertThat(request.position()).isEqualTo(ExecuteCommandRequestEncoder.positionNullValue());

        assertThat(request.getCommand()).containsOnly(
                entry("state", "CREATE"),
                entry("name", "newTopic"),
                entry("partitions", 14));

        assertThat(responseEvent.getMetadata().getKey()).isEqualTo(123L);
        assertThat(responseEvent.getMetadata().getTopicName()).isEqualTo(Protocol.SYSTEM_TOPIC);
        assertThat(responseEvent.getMetadata().getPartitionId()).isEqualTo(Protocol.SYSTEM_PARTITION);
        assertThat(responseEvent.getMetadata().getPosition()).isEqualTo(456);

        assertThat(responseEvent.getState()).isEqualTo("CREATED");
    }

    @Test
    public void shouldValidateTopicNameNotNull()
    {
        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("name must not be null");

        // when
        clientRule.topics()
            .create(null, 3)
            .execute();
    }
}
