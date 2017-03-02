package org.camunda.tngp.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.subscription.RecordingEventHandler.RecordedEvent;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.event.TopicEventType;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TopicSubscriptionRaftEventTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public Timeout timeout = Timeout.seconds(10);

    protected TngpClient client;
    protected RecordingEventHandler recordingHandler;
    protected ObjectMapper objectMapper;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
        this.recordingHandler = new RecordingEventHandler();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * There should always be two raft events on each log by default:
     * One that marks that this broker is the leader for this log.
     * The second that the leader uses to claim that it is indeed the leader.
     */
    @Test
    public void shouldReceiveRaftEvents()
    {
        // given
        client.topic(0).newSubscription()
            .startAtHeadOfTopic()
            .handler(recordingHandler)
            .open();

        // when
        TestUtil.waitUntil(() -> recordingHandler.numRecordedRaftEvents() == 2);

        // then
        final List<RecordedEvent> raftEvents = recordingHandler.getRecordedEvents()
                .stream()
                .filter((re) -> re.getMetadata().getEventType() == TopicEventType.RAFT)
                .collect(Collectors.toList());

        assertThat(isJsonObject(raftEvents.get(0).getEvent().getJson())).isTrue();
        assertThat(isJsonObject(raftEvents.get(1).getEvent().getJson())).isTrue();
    }

    protected boolean isJsonObject(String json)
    {
        try
        {
            return json != null && objectMapper.readTree(json).isObject();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
