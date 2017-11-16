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

import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
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
    public void shouldExpireLockedTask() throws InterruptedException
    {
        // given
        ClockUtil.setCurrentTime(Instant.now());

        final String taskType = "foo";
        final long taskKey1 = createTask(taskType);

        final long lockTime = 1000L;

        apiRule.openTaskSubscription(
            apiRule.getDefaultPartitionId(),
            taskType,
            lockTime);

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
        apiRule.moveMessageStreamToTail();

        // when expired
        ClockUtil.addTime(Duration.ofSeconds(TaskQueueManagerService.LOCK_EXPIRATION_INTERVAL + 1));

        // then locked again
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);

        final List<SubscribedEvent> events = apiRule.topic()
                                                     .receiveEvents(TestTopicClient.taskEvents())
                                                     .limit(8)
                                                     .collect(Collectors.toList());

        assertThat(events).extracting(e -> e.key()).contains(taskKey1);
        assertThat(events).extracting(e -> e.event().get("state"))
                          .containsExactly("CREATE", "CREATED", "LOCK", "LOCKED", "EXPIRE_LOCK", "LOCK_EXPIRED", "LOCK", "LOCKED");
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
                apiRule.getDefaultPartitionId(),
                taskType,
                lockTime);

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2); // both tasks locked
        apiRule.moveMessageStreamToTail();

        // when
        ClockUtil.addTime(Duration.ofSeconds(TaskQueueManagerService.LOCK_EXPIRATION_INTERVAL + 1));

        // then
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2);

        final List<SubscribedEvent> events = apiRule.topic()
                                                    .receiveEvents(TestTopicClient.taskEvents())
                                                    .limit(16)
                                                    .collect(Collectors.toList());

        assertThat(events).extracting(e -> e.key()).contains(taskKey1, taskKey2);
        assertThat(events).extracting(e -> e.event().get("state"))
                          .containsExactlyInAnyOrder("CREATE", "CREATED", "CREATE", "CREATED",
                                           "LOCK", "LOCK", "LOCKED", "LOCKED",
                                           "EXPIRE_LOCK", "EXPIRE_LOCK", "LOCK_EXPIRED", "LOCK_EXPIRED",
                                           "LOCK", "LOCKED", "LOCK", "LOCKED");

    }

    protected long createTask(String type)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
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
