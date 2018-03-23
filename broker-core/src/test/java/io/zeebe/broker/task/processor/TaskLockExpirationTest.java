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
package io.zeebe.broker.task.processor;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.task.TaskQueueManagerService;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedEvent;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;

public class TaskLockExpirationTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldExpireLock() throws InterruptedException
    {
        // given
        brokerRule.getClock().pinCurrentTime();
        final Duration lockTime = Duration.ofSeconds(60);
        final String taskType = "taskType";

        final long taskKey = createTask(taskType);

        apiRule.openTaskSubscription(apiRule.getDefaultPartitionId(), taskType, lockTime.toMillis()).await();
        apiRule.subscribedEvents().findFirst().get(); // => task is locked

        // when
        brokerRule.getClock().addTime(lockTime.plus(Duration.ofSeconds(1)));

        // then the task was locked and pushed again
        final List<SubscribedEvent> events =
            doRepeatedly(() ->
                apiRule.moveMessageStreamToHead()
                    .subscribedEvents()
                    .limit(2)
                    .collect(Collectors.toList()))
                .until(tasks -> tasks.size() == 2);

        assertThat(events.get(0).key()).isEqualTo(taskKey);
        assertThat(events.get(1).key()).isEqualTo(taskKey);

        apiRule.openTopicSubscription("foo", 0).await();

        final int expectedTopicEvents = 8;

        final List<SubscribedEvent> taskEvents = doRepeatedly(() -> apiRule
                .moveMessageStreamToHead()
                .subscribedEvents()
                .filter(e -> e.subscriptionType() == SubscriptionType.TOPIC_SUBSCRIPTION)
                .limit(expectedTopicEvents)
                .collect(Collectors.toList()))
            .until(e -> e.size() == expectedTopicEvents);

        assertThat(taskEvents).extracting(e -> e.event().get("state"))
            .containsExactly(
                    "CREATE",
                    "CREATED",
                    "LOCK",
                    "LOCKED",
                    "EXPIRE_LOCK",
                    "LOCK_EXPIRED",
                    "LOCK",
                    "LOCKED");
    }

    @Test
    public void shouldNotExpireLockIfLockNotExceeded() throws InterruptedException
    {
        // given
        brokerRule.getClock().pinCurrentTime();
        final Duration lockTime = Duration.ofSeconds(60);
        final String taskType = "taskType";

        createTask(taskType);

        apiRule.openTaskSubscription(apiRule.getDefaultPartitionId(), taskType, lockTime.toMillis()).await();
        apiRule.subscribedEvents().findFirst().get(); // => task is locked

        // when
        brokerRule.getClock().addTime(lockTime.minus(Duration.ofSeconds(1)));

        // then
        assertNoMoreTaskReceived();
    }

    @Test
    public void shouldNotExpireLockIfTaskCompleted() throws InterruptedException
    {
        // given
        brokerRule.getClock().pinCurrentTime();
        final Duration lockTime = Duration.ofSeconds(60);
        final String taskType = "taskType";

        createTask(taskType);

        apiRule.openTaskSubscription(apiRule.getDefaultPartitionId(), taskType, lockTime.toMillis()).await();
        final SubscribedEvent lockedTask = apiRule.subscribedEvents().findFirst().get(); // => task is locked

        completeTask(lockedTask);

        // when
        brokerRule.getClock().addTime(lockTime.plus(Duration.ofSeconds(1)));

        // then
        assertNoMoreTaskReceived();
    }

    @Test
    public void shouldNotExpireLockIfTaskFailed()
    {
        // given
        brokerRule.getClock().pinCurrentTime();
        final Duration lockTime = Duration.ofSeconds(60);
        final String taskType = "taskType";

        createTask(taskType);

        apiRule.openTaskSubscription(apiRule.getDefaultPartitionId(), taskType, lockTime.toMillis()).await();
        final SubscribedEvent lockedTask = apiRule.subscribedEvents().findFirst().get(); // => task is locked

        final Map<String, Object> event = new HashMap<>(lockedTask.event());
        event.put("retries", 0);
        failTask(lockedTask.key(), event);

        // when
        brokerRule.getClock().addTime(lockTime.plus(Duration.ofSeconds(1)));

        // then
        assertNoMoreTaskReceived();
    }

    @Test
    public void shouldExpireLockedTask()
    {
        // given
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
        doRepeatedly(() ->
        {
            brokerRule.getClock().addTime(TaskQueueManagerService.LOCK_EXPIRATION_INTERVAL);
        }).until(v -> apiRule.numSubscribedEventsAvailable() == 1);


        // then locked again
        final List<SubscribedEvent> events = apiRule.topic()
                                                     .receiveEvents(TestTopicClient.taskEvents())
                                                     .limit(8)
                                                     .collect(Collectors.toList());

        assertThat(events).extracting(e -> e.key()).contains(taskKey1);
        assertThat(events).extracting(e -> e.event().get("state"))
                          .containsExactly("CREATE", "CREATED", "LOCK", "LOCKED", "EXPIRE_LOCK", "LOCK_EXPIRED", "LOCK", "LOCKED");
    }

    @Test
    public void shouldExpireMultipleLockedTasksAtOnce()
    {
        // given
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
        doRepeatedly(() ->
        {
            brokerRule.getClock().addTime(TaskQueueManagerService.LOCK_EXPIRATION_INTERVAL);
        }).until(v -> apiRule.numSubscribedEventsAvailable() == 2);

        // then
        final List<SubscribedEvent> expiredEvents = apiRule.topic()
                                                    .receiveEvents(TestTopicClient.taskEvents())
                                                    .limit(16)
                                                    .collect(Collectors.toList());

        assertThat(expiredEvents)
            .filteredOn(e -> e.event().get("state").equals("LOCKED"))
            .hasSize(4)
            .extracting(e -> e.key()).containsExactly(taskKey1, taskKey2, taskKey1, taskKey2);

        assertThat(expiredEvents)
            .filteredOn(e -> e.event().get("state").equals("LOCK_EXPIRED"))
            .extracting(e -> e.key()).containsExactlyInAnyOrder(taskKey1, taskKey2);
    }

    private long createTask(String type)
    {
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
            .eventTypeTask()
            .command()
                .put("state", "CREATE")
                .put("type", type)
                .put("retries", 3)
                .done()
            .sendAndAwait();

        return resp.key();
    }

    private void completeTask(SubscribedEvent lockedTask)
    {
        apiRule.createCmdRequest()
            .eventTypeTask()
            .key(lockedTask.key())
            .command()
                .putAll(lockedTask.event())
                .put("state", "COMPLETE")
                .done()
            .sendAndAwait();
    }

    private void failTask(long key, Map<String, Object> event)
    {
        apiRule.createCmdRequest()
            .eventTypeTask()
            .key(key)
            .command()
                .putAll(event)
                .put("state", "FAIL")
                .done()
            .sendAndAwait();
    }

    private void assertNoMoreTaskReceived()
    {
        try
        {
            Thread.sleep(1000L);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }

        assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(0);
    }

}
