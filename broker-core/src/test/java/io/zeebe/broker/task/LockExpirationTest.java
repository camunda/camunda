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
package io.zeebe.broker.task;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedEvent;
import io.zeebe.util.time.ClockUtil;

public class LockExpirationTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @After
    public void tearDown()
    {
        ClockUtil.reset();
    }

    @Test
    public void shouldExpireMultipleLockedTasksAtOnce() throws InterruptedException
    {
        // given
        ClockUtil.setCurrentTime(Instant.now());

        final String taskType = "foo";
        final long taskKey1 = createTask(taskType);
        final long taskKey2 = createTask(taskType);

        final long lockTime = 1000L;

        apiRule.openTaskSubscription(
                ClientApiRule.DEFAULT_TOPIC_NAME,
                ClientApiRule.DEFAULT_PARTITION_ID,
                taskType,
                lockTime);

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2); // both tasks locked
        apiRule.moveMessageStreamToTail();

        ClockUtil.addTime(Duration.ofSeconds(TaskQueueManagerService.LOCK_EXPIRATION_INTERVAL + 1));

        // when
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2);

        // then
        final List<SubscribedEvent> lockEvents = apiRule.subscribedEvents().limit(2).collect(Collectors.toList());

        assertThat(lockEvents).hasSize(2);

        assertThat(lockEvents).extracting(e -> e.key()).contains(taskKey1, taskKey2);
        assertThat(lockEvents).extracting(e -> e.event().get("state")).containsExactly("LOCKED", "LOCKED");


    }

    protected long createTask(String type)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicName(ClientApiRule.DEFAULT_TOPIC_NAME)
            .partitionId(ClientApiRule.DEFAULT_PARTITION_ID)
            .eventTypeTask()
            .command()
                .put("state", "CREATE")
                .put("type", type)
                .put("retries", 3)
                .done()
            .sendAndAwait();

        return response.key();
    }

}
