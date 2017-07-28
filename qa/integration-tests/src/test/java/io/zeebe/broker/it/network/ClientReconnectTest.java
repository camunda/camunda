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
package io.zeebe.broker.it.network;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.test.util.TestUtil;

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
        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .addCustomHeader("k1", "a")
            .addCustomHeader("k2", "b")
            .payload("{ \"payload\" : 123 }")
            .execute();

        return task.getMetadata().getKey();
    }
}
