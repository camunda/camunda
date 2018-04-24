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
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;

public class TaskLockExpirationTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

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
        final SubscribedRecord lockedTask = apiRule.subscribedEvents().findFirst().get(); // => task is locked

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
        final SubscribedRecord lockedTask = apiRule.subscribedEvents().findFirst().get(); // => task is locked

        final Map<String, Object> event = new HashMap<>(lockedTask.value());
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
        final List<SubscribedRecord> events = apiRule.topic()
             .receiveRecords()
             .ofTypeTask()
             .limit(8)
             .collect(Collectors.toList());

        assertThat(events).extracting(e -> e.key()).contains(taskKey1);
        assertThat(events).extracting(e -> e.intent())
            .containsExactly(
                Intent.CREATE,
                Intent.CREATED,
                Intent.LOCK,
                Intent.LOCKED,
                Intent.EXPIRE_LOCK,
                Intent.LOCK_EXPIRED,
                Intent.LOCK,
                Intent.LOCKED);
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
        final List<SubscribedRecord> expiredEvents = apiRule.topic()
                                                    .receiveRecords()
                                                    .ofTypeTask()
                                                    .limit(16)
                                                    .collect(Collectors.toList());

        assertThat(expiredEvents)
            .filteredOn(e -> e.intent() == Intent.LOCKED)
            .hasSize(4)
            .extracting(e -> e.key()).containsExactly(taskKey1, taskKey2, taskKey1, taskKey2);

        assertThat(expiredEvents)
            .filteredOn(e -> e.intent() == Intent.LOCK_EXPIRED)
            .extracting(e -> e.key()).containsExactlyInAnyOrder(taskKey1, taskKey2);
    }

    private long createTask(String type)
    {
        final ExecuteCommandResponse resp = apiRule.createCmdRequest()
            .type(ValueType.TASK, Intent.CREATE)
            .command()
                .put("type", type)
                .put("retries", 3)
                .done()
            .sendAndAwait();

        return resp.key();
    }

    private void completeTask(SubscribedRecord lockedTask)
    {
        apiRule.createCmdRequest()
            .type(ValueType.TASK, Intent.COMPLETE)
            .key(lockedTask.key())
            .command()
                .putAll(lockedTask.value())
                .done()
            .sendAndAwait();
    }

    private void failTask(long key, Map<String, Object> event)
    {
        apiRule.createCmdRequest()
            .type(ValueType.TASK, Intent.FAIL)
            .key(key)
            .command()
                .putAll(event)
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
