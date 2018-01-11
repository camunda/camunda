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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.Event;

public class PartitionsTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule(false);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule)
                                          .around(clientRule);

    /**
     * Two issues with this test:
     *
     * - https://github.com/zeebe-io/zeebe/issues/636
     * - in the default config, a log stream write buffer requires 16 MB direct memory,
     *   so the entire test requires ~ 16 GB
     */
    @Ignore("https://github.com/zeebe-io/zeebe/issues/636")
    @Test
    public void shouldCreate1000Partitions()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        final int partitions = 1000;
        final int partitionsPerTopic = 2;

        final int topics = partitions / partitionsPerTopic;

        // when
        for (int i = 0; i < topics; i++)
        {
            final String topicName = "topic" + i;
            final Event result = client.topics()
                .create(topicName, partitionsPerTopic)
                .execute();

            // then
            assertThat(result.getState()).isEqualTo("CREATED");
        }
    }
}
