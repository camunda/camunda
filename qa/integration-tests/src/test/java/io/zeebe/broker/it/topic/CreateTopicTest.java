/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.topic;

import static io.zeebe.protocol.Protocol.SYSTEM_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.commands.Partition;
import io.zeebe.client.api.commands.Topic;
import io.zeebe.client.api.commands.Topics;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobEvent.JobState;
import io.zeebe.client.api.events.TopicEvent;
import io.zeebe.client.api.events.TopicEvent.TopicState;

public class CreateTopicTest
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
    public Timeout testTimeout = Timeout.seconds(20);

    private ZeebeClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @Test
    public void shouldCreateTaskOnNewTopic()
    {
        // given
        final String topicName = "newTopic";
        client.newCreateTopicCommand()
            .name(topicName)
            .partitions(2)
            .replicationFactor(1)
            .send()
            .join();

        // when
        final JobEvent jobEvent = client.topicClient(topicName).jobClient()
            .newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        // then
        assertThat(jobEvent.getState()).isEqualTo(JobState.CREATED);
    }

    @Test
    public void shouldCreateMultipleTopicsInParallel() throws Exception
    {
        // when
        final ZeebeFuture<TopicEvent> fooFuture = client.newCreateTopicCommand()
            .name("foo")
            .partitions(2)
            .replicationFactor(1)
            .send();

        final ZeebeFuture<TopicEvent> barFuture = client.newCreateTopicCommand()
            .name("bar")
            .partitions(2)
            .replicationFactor(1)
            .send();

        // then
        assertThat(fooFuture.get(10, TimeUnit.SECONDS).getState()).isEqualTo(TopicState.CREATING);
        assertThat(barFuture.get(10, TimeUnit.SECONDS).getState()).isEqualTo(TopicState.CREATING);

        // and
        clientRule.waitUntilTopicsExists("foo", "bar");

        // then
        final Topics topics = client.newTopicsRequest().send().join();

        final Optional<Topic> fooTopic = topics.getTopics().stream().filter(t -> t.getName().equals("foo")).findFirst();
        assertThat(fooTopic).hasValueSatisfying(t -> assertThat(t.getPartitions()).hasSize(2));

        final Optional<Topic> barTopic = topics.getTopics().stream().filter(t -> t.getName().equals("bar")).findFirst();
        assertThat(barTopic).hasValueSatisfying(t -> assertThat(t.getPartitions()).hasSize(2));

    }

    @Test
    public void shouldRequestTopics()
    {
        // given
        client.newCreateTopicCommand()
            .name("foo")
            .partitions(2)
            .replicationFactor(1)
            .send();

        clientRule.waitUntilTopicsExists("foo");

        // when
        final Map<String, List<Partition>> topics = clientRule.topicsByName();

        // then
        assertThat(topics).hasSize(3);
        assertThat(topics.get(SYSTEM_TOPIC)).hasSize(1);
        assertThat(topics.get(clientRule.getDefaultTopic())).hasSize(1);
        assertThat(topics.get("foo")).hasSize(2);
    }

}
