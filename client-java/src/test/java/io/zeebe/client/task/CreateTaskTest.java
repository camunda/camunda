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
package io.zeebe.client.task;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_PARTITION_ID;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;

import java.util.HashMap;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CreateTaskTest
{

    public ClientRule clientRule = new ClientRule();
    public StubBrokerRule brokerRule = new StubBrokerRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

    protected ZeebeClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @Test
    public void shouldCreateTask()
    {
        // given
        brokerRule.onExecuteCommandRequest(ecr -> ecr.eventType() == EventType.TASK_EVENT &&
                "CREATE".equals(ecr.getCommand().get("eventType")))
            .respondWith()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .key(123)
            .event()
              .allOf((r) -> r.getCommand())
              .put("eventType", "CREATED")
              .put("headers", new HashMap<>())
              .put("payload", new byte[0])
              .done()
            .register();

        // when
        final Long taskKey = clientRule.taskTopic()
            .create()
            .taskType("foo")
            .retries(3)
            .execute();

        // then
        assertThat(taskKey).isEqualTo(123L);
    }
}
