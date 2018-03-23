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
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;

import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.task.TaskQueueManagerService;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.util.buffer.BufferUtil;

public class TaskLockExpirationStreamProcessorTest
{

    @Rule
    public StreamProcessorRule rule = new StreamProcessorRule();

    private TaskEvent taskLocked()
    {
        final TaskEvent event = new TaskEvent();

        event.setState(TaskState.LOCKED);
        event.setType(BufferUtil.wrapString("foo"));

        return event;
    }

    @Test
    public void shouldExpireLockIfAfterLockTimeForTwoTasks()
    {
        // given
        rule.getClock().pinCurrentTime();

        rule.runStreamProcessor(e -> new TaskExpireLockStreamProcessor(
                e.buildStreamReader(),
                e.buildStreamWriter())
            .createStreamProcessor(e));

        rule.writeEvent(1, taskLocked());
        rule.writeEvent(2, taskLocked());

        // when
        rule.getClock().addTime(TaskQueueManagerService.LOCK_EXPIRATION_INTERVAL.plus(Duration.ofSeconds(1)));

        // then
        final List<TypedEvent<TaskEvent>> expirationEvents = doRepeatedly(
            () -> rule.events()
                .onlyTaskEvents()
                .inState(TaskState.EXPIRE_LOCK)
                .collect(Collectors.toList()))
            .until(l -> l.size() == 2);

        assertThat(expirationEvents).extracting("key").containsExactlyInAnyOrder(1L, 2L);
    }
}
