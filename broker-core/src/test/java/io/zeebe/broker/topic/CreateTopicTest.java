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
package io.zeebe.broker.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;

public class CreateTopicTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldCreateTopic()
    {
        // given
        final String topicName = "newTopic";

        // when
        final ExecuteCommandResponse response = createTopic(topicName, 2);

        // then
        assertThat(response.getEvent())
            .containsExactly(
                entry("state", "CREATED"),
                entry("name", topicName),
                entry("partitions", 2)
            );
    }

    @Test
    public void shouldNotCreateSystemTopic()
    {
        // when
        final ExecuteCommandResponse response = createTopic(Protocol.SYSTEM_TOPIC, 2);

        // then
        assertThat(response.getEvent())
            .containsExactly(
                entry("state", "CREATE_REJECTED"),
                entry("name", Protocol.SYSTEM_TOPIC),
                entry("partitions", 2)
            );
    }

    @Test
    public void shouldNotCreateExistingTopic() throws InterruptedException
    {
        // given
        final String topicName = "newTopic";
        createTopic(topicName, 2);

        // when
        final ExecuteCommandResponse response = createTopic(topicName, 2);

        // then
        assertThat(response.getEvent())
            .containsExactly(
                entry("state", "CREATE_REJECTED"),
                entry("name", topicName),
                entry("partitions", 2)
            );
    }

    @Test
    public void shouldNotCreateTopicWithZeroPartitions()
    {
        // given
        final String topicName = "newTopic";
        final int numberOfPartitions = 0;

        // when
        final ExecuteCommandResponse response = createTopic(topicName, numberOfPartitions);

        // then
        assertThat(response.getEvent())
            .containsExactly(
                entry("state", "CREATE_REJECTED"),
                entry("name", topicName),
                entry("partitions", numberOfPartitions)
            );
    }

    @Test
    public void shouldNotCreateTopicWithNegativePartitions()
    {
        // given
        final String topicName = "newTopic";
        final int numberOfPartitions = -100;

        // when
        final ExecuteCommandResponse response = createTopic(topicName, numberOfPartitions);

        // then
        assertThat(response.getEvent())
            .containsExactly(
                entry("state", "CREATE_REJECTED"),
                entry("name", topicName),
                entry("partitions", numberOfPartitions)
            );
    }

    @Test
    public void shouldCreateTopicAfterRejection()
    {

        // given a rejected creation request
        final String topicName = "newTopic";
        createTopic(topicName, 0);

        // when I send a valid creation request for the same topic
        final ExecuteCommandResponse response = createTopic(topicName, 1);

        // then this is successful
        assertThat(response.getEvent())
            .containsExactly(
                entry("state", "CREATED"),
                entry("name", topicName),
                entry("partitions", 1)
            );

    }

    protected ExecuteCommandResponse createTopic(String name, int partitions)
    {
        return apiRule.createCmdRequest()
            .topicName(Protocol.SYSTEM_TOPIC)
            .partitionId(Protocol.SYSTEM_PARTITION)
            .eventType(EventType.TOPIC_EVENT)
            .command()
                .put("state", "CREATE")
                .put("name", name)
                .put("partitions", partitions)
                .done()
            .sendAndAwait();
    }
}
