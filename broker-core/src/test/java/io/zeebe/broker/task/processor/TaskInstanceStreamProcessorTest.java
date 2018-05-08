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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.data.TaskRecord;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.util.buffer.BufferUtil;

public class TaskInstanceStreamProcessorTest
{

    @Rule
    public StreamProcessorRule rule = new StreamProcessorRule();

    @Mock
    public TaskSubscriptionManager subscriptionManager;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        when(subscriptionManager.increaseSubscriptionCreditsAsync(any())).thenReturn(true);
    }

    @Test
    public void shouldCompleteExpiredTask()
    {
        // given
        rule.getClock().pinCurrentTime();

        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, Intent.CREATE, task());
        waitForEventWithIntent(Intent.CREATED);

        final TaskRecord lockedTask = lockedTask(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, Intent.LOCK, lockedTask);
        waitForEventWithIntent(Intent.LOCKED);

        rule.writeCommand(key, Intent.EXPIRE_LOCK, lockedTask);
        waitForEventWithIntent(Intent.LOCK_EXPIRED);

        // when
        rule.writeCommand(key, Intent.COMPLETE, lockedTask);

        // then
        waitForEventWithIntent(Intent.COMPLETED);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, Intent.CREATE),
                    tuple(RecordType.EVENT, Intent.CREATED),
                    tuple(RecordType.COMMAND, Intent.LOCK),
                    tuple(RecordType.EVENT, Intent.LOCKED),
                    tuple(RecordType.COMMAND, Intent.EXPIRE_LOCK),
                    tuple(RecordType.EVENT, Intent.LOCK_EXPIRED),
                    tuple(RecordType.COMMAND, Intent.COMPLETE),
                    tuple(RecordType.EVENT, Intent.COMPLETED));
    }

    @Test
    public void shouldLockOnlyOnce()
    {
        // given
        rule.getClock().pinCurrentTime();

        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);
        control.blockAfterTaskEvent(e -> e.getMetadata().getIntent() == Intent.CREATED);

        rule.writeCommand(key, Intent.CREATE, task());
        waitForEventWithIntent(Intent.CREATED);

        // when
        rule.writeCommand(key, Intent.LOCK, lockedTask(nowPlus(Duration.ofSeconds(30))));
        rule.writeCommand(key, Intent.LOCK, lockedTask(nowPlus(Duration.ofSeconds(30))));
        control.unblock();

        // then
        waitForRejection(Intent.LOCK);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, Intent.CREATE),
                    tuple(RecordType.EVENT, Intent.CREATED),
                    tuple(RecordType.COMMAND, Intent.LOCK),
                    tuple(RecordType.COMMAND, Intent.LOCK),
                    tuple(RecordType.EVENT, Intent.LOCKED),
                    tuple(RecordType.COMMAND_REJECTION, Intent.LOCK));
    }

    @Test
    public void shouldRejectLockIfTaskNotFound()
    {
        // given
        rule.getClock().pinCurrentTime();

        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        // when
        rule.writeCommand(key, Intent.LOCK, lockedTask(nowPlus(Duration.ofSeconds(30))));

        // then
        waitForRejection(Intent.LOCK);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, Intent.LOCK),
                    tuple(RecordType.COMMAND_REJECTION, Intent.LOCK));
    }

    @Test
    public void shouldExpireLockOnlyOnce()
    {
        // given
        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, Intent.CREATE, task());
        waitForEventWithIntent(Intent.CREATED);

        control.blockAfterTaskEvent(e -> e.getMetadata().getIntent() == Intent.LOCKED);

        final TaskRecord lockedTask = lockedTask(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, Intent.LOCK, lockedTask);
        waitForEventWithIntent(Intent.LOCKED);

        // when
        rule.writeCommand(key, Intent.EXPIRE_LOCK, lockedTask);
        rule.writeCommand(key, Intent.EXPIRE_LOCK, lockedTask);
        control.unblock();

        // then
        waitForRejection(Intent.EXPIRE_LOCK);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, Intent.CREATE),
                    tuple(RecordType.EVENT, Intent.CREATED),
                    tuple(RecordType.COMMAND, Intent.LOCK),
                    tuple(RecordType.EVENT, Intent.LOCKED),
                    tuple(RecordType.COMMAND, Intent.EXPIRE_LOCK),
                    tuple(RecordType.COMMAND, Intent.EXPIRE_LOCK),
                    tuple(RecordType.EVENT, Intent.LOCK_EXPIRED),
                    tuple(RecordType.COMMAND_REJECTION, Intent.EXPIRE_LOCK));
    }

    private Instant nowPlus(Duration duration)
    {
        return rule.getClock().getCurrentTime().plus(duration);
    }

    @Test
    public void shouldRejectExpireCommandIfTaskCreated()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, Intent.CREATE, task());
        waitForEventWithIntent(Intent.CREATED);

        // when
        rule.writeCommand(key, Intent.EXPIRE_LOCK, task());

        // then
        waitForRejection(Intent.EXPIRE_LOCK);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, Intent.CREATE),
                    tuple(RecordType.EVENT, Intent.CREATED),
                    tuple(RecordType.COMMAND, Intent.EXPIRE_LOCK),
                    tuple(RecordType.COMMAND_REJECTION, Intent.EXPIRE_LOCK));
    }

    @Test
    public void shouldRejectExpireCommandIfTaskCompleted()
    {
        // given
        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, Intent.CREATE, task());
        waitForEventWithIntent(Intent.CREATED);

        control.blockAfterTaskEvent(e -> e.getMetadata().getIntent() == Intent.LOCKED);

        final TaskRecord lockedTask = lockedTask(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, Intent.LOCK, lockedTask);
        waitForEventWithIntent(Intent.LOCKED);

        // when
        rule.writeCommand(key, Intent.COMPLETE, lockedTask);
        rule.writeCommand(key, Intent.EXPIRE_LOCK, lockedTask);
        control.unblock();

        // then
        waitForRejection(Intent.EXPIRE_LOCK);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, Intent.CREATE),
                    tuple(RecordType.EVENT, Intent.CREATED),
                    tuple(RecordType.COMMAND, Intent.LOCK),
                    tuple(RecordType.EVENT, Intent.LOCKED),
                    tuple(RecordType.COMMAND, Intent.COMPLETE),
                    tuple(RecordType.COMMAND, Intent.EXPIRE_LOCK),
                    tuple(RecordType.EVENT, Intent.COMPLETED),
                    tuple(RecordType.COMMAND_REJECTION, Intent.EXPIRE_LOCK));
    }


    @Test
    public void shouldRejectExpireCommandIfTaskFailed()
    {
        // given
        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, Intent.CREATE, task());
        waitForEventWithIntent(Intent.CREATED);

        control.blockAfterTaskEvent(e -> e.getMetadata().getIntent() == Intent.LOCKED);

        final TaskRecord lockedTask = lockedTask(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, Intent.LOCK, lockedTask);
        waitForEventWithIntent(Intent.LOCKED);

        // when
        rule.writeCommand(key, Intent.FAIL, lockedTask);
        rule.writeCommand(key, Intent.EXPIRE_LOCK, lockedTask);
        control.unblock();

        // then
        waitForRejection(Intent.EXPIRE_LOCK);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, Intent.CREATE),
                    tuple(RecordType.EVENT, Intent.CREATED),
                    tuple(RecordType.COMMAND, Intent.LOCK),
                    tuple(RecordType.EVENT, Intent.LOCKED),
                    tuple(RecordType.COMMAND, Intent.FAIL),
                    tuple(RecordType.COMMAND, Intent.EXPIRE_LOCK),
                    tuple(RecordType.EVENT, Intent.FAILED),
                    tuple(RecordType.COMMAND_REJECTION, Intent.EXPIRE_LOCK));
    }

    @Test
    public void shouldRejectExpireCommandIfTaskNotFound()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        // when
        rule.writeCommand(key, Intent.EXPIRE_LOCK, lockedTask(nowPlus(Duration.ofSeconds(30))));

        // then
        waitForRejection(Intent.EXPIRE_LOCK);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                    tuple(RecordType.COMMAND, Intent.EXPIRE_LOCK),
                    tuple(RecordType.COMMAND_REJECTION, Intent.EXPIRE_LOCK));
    }

    @Test
    public void shouldCancelCreatedTask()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, Intent.CREATE, task());
        waitForEventWithIntent(Intent.CREATED);

        // when
        rule.writeCommand(key, Intent.CANCEL, task());

        // then
        waitForEventWithIntent(Intent.CANCELED);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                tuple(RecordType.COMMAND, Intent.CREATE),
                tuple(RecordType.EVENT, Intent.CREATED),
                tuple(RecordType.COMMAND, Intent.CANCEL),
                tuple(RecordType.EVENT, Intent.CANCELED));
    }

    @Test
    public void shouldCancelLockedTask()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, Intent.CREATE, task());
        waitForEventWithIntent(Intent.CREATED);

        final TaskRecord lockedTask = lockedTask(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, Intent.LOCK, lockedTask);
        waitForEventWithIntent(Intent.LOCKED);

        // when
        rule.writeCommand(key, Intent.CANCEL, lockedTask);

        // then
        waitForEventWithIntent(Intent.CANCELED);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                tuple(RecordType.COMMAND, Intent.CREATE),
                tuple(RecordType.EVENT, Intent.CREATED),
                tuple(RecordType.COMMAND, Intent.LOCK),
                tuple(RecordType.EVENT, Intent.LOCKED),
                tuple(RecordType.COMMAND, Intent.CANCEL),
                tuple(RecordType.EVENT, Intent.CANCELED));
    }

    @Test
    public void shouldCancelFailedTask()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, Intent.CREATE, task());
        waitForEventWithIntent(Intent.CREATED);

        final TaskRecord lockedTask = lockedTask(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, Intent.LOCK, lockedTask);
        waitForEventWithIntent(Intent.LOCKED);

        rule.writeCommand(key, Intent.FAIL, lockedTask);
        waitForEventWithIntent(Intent.FAILED);

        // when
        rule.writeCommand(key, Intent.CANCEL, lockedTask);

        // then
        waitForEventWithIntent(Intent.CANCELED);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                tuple(RecordType.COMMAND, Intent.CREATE),
                tuple(RecordType.EVENT, Intent.CREATED),
                tuple(RecordType.COMMAND, Intent.LOCK),
                tuple(RecordType.EVENT, Intent.LOCKED),
                tuple(RecordType.COMMAND, Intent.FAIL),
                tuple(RecordType.EVENT, Intent.FAILED),
                tuple(RecordType.COMMAND, Intent.CANCEL),
                tuple(RecordType.EVENT, Intent.CANCELED));
    }


    @Test
    public void shouldRejectCancelIfTaskCompleted()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeCommand(key, Intent.CREATE, task());
        waitForEventWithIntent(Intent.CREATED);

        final TaskRecord lockedTask = lockedTask(nowPlus(Duration.ofSeconds(30)));
        rule.writeCommand(key, Intent.LOCK, lockedTask);
        waitForEventWithIntent(Intent.LOCKED);

        rule.writeCommand(key, Intent.COMPLETE, lockedTask);
        waitForEventWithIntent(Intent.COMPLETED);

        // when
        rule.writeCommand(key, Intent.CANCEL, lockedTask);

        // then
        waitForRejection(Intent.CANCEL);

        final List<TypedRecord<TaskRecord>> taskEvents = rule.events().onlyTaskRecords().collect(Collectors.toList());
        assertThat(taskEvents).extracting(r -> r.getMetadata()).extracting(m -> m.getRecordType(), m -> m.getIntent())
            .containsExactly(
                tuple(RecordType.COMMAND, Intent.CREATE),
                tuple(RecordType.EVENT, Intent.CREATED),
                tuple(RecordType.COMMAND, Intent.LOCK),
                tuple(RecordType.EVENT, Intent.LOCKED),
                tuple(RecordType.COMMAND, Intent.COMPLETE),
                tuple(RecordType.EVENT, Intent.COMPLETED),
                tuple(RecordType.COMMAND, Intent.CANCEL),
                tuple(RecordType.COMMAND_REJECTION, Intent.CANCEL));
    }

    private void waitForEventWithIntent(Intent intent)
    {
        waitUntil(() -> rule.events().onlyTaskRecords().onlyEvents().withIntent(intent).findFirst().isPresent());
    }

    public void waitForRejection(Intent commandIntent)
    {
        waitUntil(() -> rule.events().onlyTaskRecords().onlyRejections().withIntent(commandIntent).findFirst().isPresent());
    }

    private TaskRecord task()
    {
        final TaskRecord event = new TaskRecord();

        event.setType(BufferUtil.wrapString("foo"));

        return event;
    }

    private TaskRecord lockedTask(Instant lockTime)
    {
        final TaskRecord event = new TaskRecord();

        event.setType(BufferUtil.wrapString("foo"));
        event.setLockOwner(BufferUtil.wrapString("bar"));
        event.setLockTime(lockTime.toEpochMilli());

        return event;
    }

    private StreamProcessor buildStreamProcessor(TypedStreamEnvironment env)
    {
        return new TaskInstanceStreamProcessor(subscriptionManager).createStreamProcessor(env);
    }


}
