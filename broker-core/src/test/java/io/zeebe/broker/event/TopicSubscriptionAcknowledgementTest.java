/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.event;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_TOPIC_NAME;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class TopicSubscriptionAcknowledgementTest
{
    protected static final String SUBSCRIPTION_NAME = "foo";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    protected long subscriberKey;

    @Before
    public void openSubscription()
    {
        openSubscription(0);
    }

    public void openSubscription(long startPosition)
    {
        final ExecuteCommandResponse response = apiRule
                .openTopicSubscription(SUBSCRIPTION_NAME, startPosition)
                .await();
        subscriberKey = response.key();
    }


    protected void closeSubscription()
    {
        apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .data()
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", DEFAULT_PARTITION_ID)
                .put("subscriberKey", subscriberKey)
                .done()
            .sendAndAwait();
    }

    @Test
    public void shouldAcknowledgePosition()
    {
        // when
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .eventTypeSubscription()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .command()
                .put("name", SUBSCRIPTION_NAME)
                .put("state", "ACKNOWLEDGE")
                .put("ackPosition", 0)
                .done()
            .sendAndAwait();

        // then
        assertThat(response.getEvent()).containsEntry("name", SUBSCRIPTION_NAME);
        assertThat(response.getEvent()).containsEntry("state", "ACKNOWLEDGED");
    }

    @Test
    public void shouldResumeAfterAcknowledgedPosition()
    {
        // given
        createTask();

        final List<SubscribedEvent> events = apiRule
                .subscribedEvents()
                .limit(2L)
                .collect(Collectors.toList());

        apiRule.createCmdRequest()
            .eventTypeSubscription()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .command()
                .put("name", SUBSCRIPTION_NAME)
                .put("state", "ACKNOWLEDGE")
                .put("ackPosition", events.get(0).position())
                .done()
            .sendAndAwait();

        closeSubscription();

        apiRule.moveMessageStreamToTail();

        // when
        openSubscription();

        // then
        final Optional<SubscribedEvent> firstEvent = apiRule
                .subscribedEvents()
                .findFirst();

        assertThat(firstEvent).isPresent();
        assertThat(firstEvent.get().position()).isEqualTo(events.get(1).position());
    }

    @Test
    public void shouldResumeAtTailOnLongMaxAckPosition()
    {
        // given
        apiRule.createCmdRequest()
            .eventTypeSubscription()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .command()
                .put("name", SUBSCRIPTION_NAME)
                .put("state", "ACKNOWLEDGE")
                .put("ackPosition", Long.MAX_VALUE)
                .done()
            .sendAndAwait();

        closeSubscription();

        apiRule.moveMessageStreamToTail();

        // when
        openSubscription();

        // and
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .eventTypeTask()
            .command()
                .put("state", "CREATE")
                .put("type", "theTaskType")
                .done()
            .sendAndAwait();

        final long taskKey = response.key();

        // then
        final Optional<SubscribedEvent> firstEvent = apiRule
                .subscribedEvents()
                .findFirst();

        assertThat(firstEvent).isPresent();
        assertThat(firstEvent.get().key()).isEqualTo(taskKey);
    }

    @Test
    public void shouldPersistStartPosition()
    {
        // given
        createTask();

        final List<Long> taskEventPositions = apiRule.subscribedEvents()
            .filter((e) -> e.eventType() == EventType.TASK_EVENT)
            .map((e) -> e.position())
            .limit(2)
            .collect(Collectors.toList());

        closeSubscription();
        apiRule.moveMessageStreamToTail();

        // when
        openSubscription(taskEventPositions.get(1));

        // then it begins at the original offset (we didn't send any ACK before)
        final List<Long> taskEventPositionsAfterReopen = apiRule.subscribedEvents()
            .filter((e) -> e.eventType() == EventType.TASK_EVENT)
            .map((e) -> e.position())
            .limit(2)
            .collect(Collectors.toList());

        assertThat(taskEventPositionsAfterReopen).containsExactlyElementsOf(taskEventPositions);
    }

    private ExecuteCommandResponse createTask()
    {
        return apiRule.createCmdRequest()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .eventTypeTask()
            .command()
                .put("state", "CREATE")
                .put("type", "foo")
                .put("retries", 1)
                .done()
            .sendAndAwait();
    }

}
