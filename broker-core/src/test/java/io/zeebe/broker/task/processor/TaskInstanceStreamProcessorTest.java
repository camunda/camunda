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

import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.task.TaskSubscriptionManager;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.logstreams.processor.StreamProcessor;
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

        rule.writeEvent(key, create());
        waitForEventInState(TaskState.CREATED);

        rule.writeEvent(key, lock(nowPlus(Duration.ofSeconds(30))));
        waitForEventInState(TaskState.LOCKED);

        rule.writeEvent(key, expireLock());
        waitForEventInState(TaskState.LOCK_EXPIRED);

        // when
        rule.writeEvent(key, complete());

        // then
        waitForEventInState(TaskState.COMPLETED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                    TaskState.CREATE,
                    TaskState.CREATED,
                    TaskState.LOCK,
                    TaskState.LOCKED,
                    TaskState.EXPIRE_LOCK,
                    TaskState.LOCK_EXPIRED,
                    TaskState.COMPLETE,
                    TaskState.COMPLETED);
    }

    @Test
    public void shouldLockOnlyOnce()
    {
        // given
        rule.getClock().pinCurrentTime();

        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);
        control.blockAfterTaskEvent(e -> e.getValue().getState() == TaskState.CREATED);

        rule.writeEvent(key, create());
        waitForEventInState(TaskState.CREATED);

        // when
        rule.writeEvent(key, lock(nowPlus(Duration.ofSeconds(30))));
        rule.writeEvent(key, lock(nowPlus(Duration.ofSeconds(30))));
        control.unblock();

        // then
        waitForEventInState(TaskState.LOCK_REJECTED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                    TaskState.CREATE,
                    TaskState.CREATED,
                    TaskState.LOCK,
                    TaskState.LOCK,
                    TaskState.LOCKED,
                    TaskState.LOCK_REJECTED);
    }

    @Test
    public void shouldRejectLockIfTaskNotFound()
    {
        // given
        rule.getClock().pinCurrentTime();

        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        // when
        rule.writeEvent(key, lock(nowPlus(Duration.ofSeconds(30))));

        // then
        waitForEventInState(TaskState.LOCK_REJECTED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                    TaskState.LOCK,
                    TaskState.LOCK_REJECTED);
    }

    @Test
    public void shouldExpireLockOnlyOnce()
    {
        // given
        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(key, create());
        waitForEventInState(TaskState.CREATED);

        control.blockAfterTaskEvent(e -> e.getValue().getState() == TaskState.LOCKED);
        rule.writeEvent(key, lock(nowPlus(Duration.ofSeconds(30))));
        waitForEventInState(TaskState.LOCKED);

        // when
        rule.writeEvent(key, expireLock());
        rule.writeEvent(key, expireLock());
        control.unblock();

        // then
        waitForEventInState(TaskState.LOCK_EXPIRATION_REJECTED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                    TaskState.CREATE,
                    TaskState.CREATED,
                    TaskState.LOCK,
                    TaskState.LOCKED,
                    TaskState.EXPIRE_LOCK,
                    TaskState.EXPIRE_LOCK,
                    TaskState.LOCK_EXPIRED,
                    TaskState.LOCK_EXPIRATION_REJECTED);
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

        rule.writeEvent(key, create());
        waitForEventInState(TaskState.CREATED);

        // when
        rule.writeEvent(key, expireLock());

        // then
        waitForEventInState(TaskState.LOCK_EXPIRATION_REJECTED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                    TaskState.CREATE,
                    TaskState.CREATED,
                    TaskState.EXPIRE_LOCK,
                    TaskState.LOCK_EXPIRATION_REJECTED);
    }

    @Test
    public void shouldRejectExpireCommandIfTaskCompleted()
    {
        // given
        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(key, create());
        waitForEventInState(TaskState.CREATED);

        control.blockAfterTaskEvent(e -> e.getValue().getState() == TaskState.LOCKED);
        rule.writeEvent(key, lock(nowPlus(Duration.ofSeconds(30))));
        waitForEventInState(TaskState.LOCKED);

        // when
        rule.writeEvent(key, complete());
        rule.writeEvent(key, expireLock());
        control.unblock();

        // then
        waitForEventInState(TaskState.LOCK_EXPIRATION_REJECTED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                    TaskState.CREATE,
                    TaskState.CREATED,
                    TaskState.LOCK,
                    TaskState.LOCKED,
                    TaskState.COMPLETE,
                    TaskState.EXPIRE_LOCK,
                    TaskState.COMPLETED,
                    TaskState.LOCK_EXPIRATION_REJECTED);
    }


    @Test
    public void shouldRejectExpireCommandIfTaskFailed()
    {
        // given
        final long key = 1;
        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(key, create());
        waitForEventInState(TaskState.CREATED);

        control.blockAfterTaskEvent(e -> e.getValue().getState() == TaskState.LOCKED);
        rule.writeEvent(key, lock(nowPlus(Duration.ofSeconds(30))));
        waitForEventInState(TaskState.LOCKED);

        // when
        rule.writeEvent(key, failure());
        rule.writeEvent(key, expireLock());
        control.unblock();

        // then
        waitForEventInState(TaskState.LOCK_EXPIRATION_REJECTED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                    TaskState.CREATE,
                    TaskState.CREATED,
                    TaskState.LOCK,
                    TaskState.LOCKED,
                    TaskState.FAIL,
                    TaskState.EXPIRE_LOCK,
                    TaskState.FAILED,
                    TaskState.LOCK_EXPIRATION_REJECTED);
    }

    @Test
    public void shouldRejectExpireCommandIfTaskNotFound()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        // when
        rule.writeEvent(key, expireLock());

        // then
        waitForEventInState(TaskState.LOCK_EXPIRATION_REJECTED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                    TaskState.EXPIRE_LOCK,
                    TaskState.LOCK_EXPIRATION_REJECTED);
    }

    @Test
    public void shouldCancelCreatedTask()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(key, create());
        waitForEventInState(TaskState.CREATED);

        // when
        rule.writeEvent(key, cancel());

        // then
        waitForEventInState(TaskState.CANCELED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                TaskState.CREATE,
                TaskState.CREATED,
                TaskState.CANCEL,
                TaskState.CANCELED);
    }

    @Test
    public void shouldCancelLockedTask()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(key, create());
        waitForEventInState(TaskState.CREATED);

        rule.writeEvent(key, lock(Instant.now().plusSeconds(30)));
        waitForEventInState(TaskState.LOCKED);

        // when
        rule.writeEvent(key, cancel());

        // then
        waitForEventInState(TaskState.CANCELED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                TaskState.CREATE,
                TaskState.CREATED,
                TaskState.LOCK,
                TaskState.LOCKED,
                TaskState.CANCEL,
                TaskState.CANCELED);
    }

    @Test
    public void shouldCancelFailedTask()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(key, create());
        waitForEventInState(TaskState.CREATED);

        rule.writeEvent(key, lock(Instant.now().plusSeconds(30)));
        waitForEventInState(TaskState.LOCKED);

        rule.writeEvent(key, failure());
        waitForEventInState(TaskState.FAILED);

        // when
        rule.writeEvent(key, cancel());

        // then
        waitForEventInState(TaskState.CANCELED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                TaskState.CREATE,
                TaskState.CREATED,
                TaskState.LOCK,
                TaskState.LOCKED,
                TaskState.FAIL,
                TaskState.FAILED,
                TaskState.CANCEL,
                TaskState.CANCELED);
    }


    @Test
    public void shouldRejectCancelIfTaskCompleted()
    {
        // given
        final long key = 1;
        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(key, create());
        waitForEventInState(TaskState.CREATED);

        rule.writeEvent(key, lock(Instant.now().plusSeconds(30)));
        waitForEventInState(TaskState.LOCKED);

        rule.writeEvent(key, complete());
        waitForEventInState(TaskState.COMPLETED);

        // when
        rule.writeEvent(key, cancel());

        // then
        waitForEventInState(TaskState.CANCEL_REJECTED);

        final List<TypedEvent<TaskEvent>> taskEvents = rule.events().onlyTaskEvents().collect(Collectors.toList());
        assertThat(taskEvents).extracting("value.state")
            .containsExactly(
                TaskState.CREATE,
                TaskState.CREATED,
                TaskState.LOCK,
                TaskState.LOCKED,
                TaskState.COMPLETE,
                TaskState.COMPLETED,
                TaskState.CANCEL,
                TaskState.CANCEL_REJECTED);
    }

    private void waitForEventInState(TaskState state)
    {
        waitUntil(() -> rule.events().onlyTaskEvents().inState(state).findFirst().isPresent());
    }

    private TaskEvent create()
    {
        final TaskEvent event = new TaskEvent();

        event.setState(TaskState.CREATE);
        event.setType(BufferUtil.wrapString("foo"));

        return event;
    }

    private TaskEvent lock(Instant lockTime)
    {
        final TaskEvent event = new TaskEvent();

        event.setState(TaskState.LOCK);
        event.setType(BufferUtil.wrapString("foo"));
        event.setLockOwner(BufferUtil.wrapString("bar"));
        event.setLockTime(lockTime.toEpochMilli());

        return event;
    }

    private TaskEvent complete()
    {
        final TaskEvent event = new TaskEvent();

        event.setState(TaskState.COMPLETE);
        event.setType(BufferUtil.wrapString("foo"));
        event.setLockOwner(BufferUtil.wrapString("bar"));

        return event;
    }

    private TaskEvent failure()
    {
        final TaskEvent event = new TaskEvent();

        event.setState(TaskState.FAIL);
        event.setType(BufferUtil.wrapString("foo"));
        event.setLockOwner(BufferUtil.wrapString("bar"));

        return event;
    }

    private TaskEvent cancel()
    {
        final TaskEvent event = new TaskEvent();

        event.setState(TaskState.CANCEL);
        event.setType(BufferUtil.wrapString("foo"));

        return event;
    }

    private TaskEvent expireLock()
    {
        final TaskEvent event = new TaskEvent();

        event.setState(TaskState.EXPIRE_LOCK);
        event.setType(BufferUtil.wrapString("foo"));
        event.setLockOwner(BufferUtil.wrapString("bar"));

        return event;
    }

    private StreamProcessor buildStreamProcessor(TypedStreamEnvironment env)
    {
        return new TaskInstanceStreamProcessor(subscriptionManager).createStreamProcessor(env);
    }


}
