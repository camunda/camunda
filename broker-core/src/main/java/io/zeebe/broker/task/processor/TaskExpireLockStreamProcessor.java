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

import static org.agrona.BitUtil.SIZE_OF_LONG;

import java.util.Iterator;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedEventProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.task.TaskQueueManagerService;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.map.Long2BytesZbMap;
import io.zeebe.map.iterator.Long2BytesZbMapEntry;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.util.sched.ScheduledTimer;
import io.zeebe.util.sched.clock.ActorClock;

public class TaskExpireLockStreamProcessor implements StreamProcessorLifecycleAware
{
    protected static final int MAP_VALUE_MAX_LENGTH = SIZE_OF_LONG + SIZE_OF_LONG;

    protected Long2BytesZbMap expirationMap = new Long2BytesZbMap(MAP_VALUE_MAX_LENGTH);

    private UnsafeBuffer mapAccessBuffer = new UnsafeBuffer(new byte[MAP_VALUE_MAX_LENGTH]);

    private final TaskEventWriter streamWriter;

    private ScheduledTimer timer;

    public TaskExpireLockStreamProcessor(TypedStreamReader streamReader, TypedStreamWriter streamWriter)
    {
        this.streamWriter = new TaskEventWriter(streamWriter, streamReader);
    }

    @Override
    public void onOpen(TypedStreamProcessor streamProcessor)
    {
        timer = streamProcessor.getActor().runAtFixedRate(TaskQueueManagerService.LOCK_EXPIRATION_INTERVAL, this::timeOutTasks);
    }

    @Override
    public void onClose()
    {
        if (timer != null)
        {
            timer.cancel();
            timer = null;
        }

        streamWriter.close();
    }

    private void timeOutTasks()
    {
        final Iterator<Long2BytesZbMapEntry> iterator = expirationMap.iterator();

        while (iterator.hasNext())
        {
            final Long2BytesZbMapEntry entry = iterator.next();

            final DirectBuffer value = entry.getValue();

            final long eventPosition = value.getLong(0);
            final long lockExpirationTime = value.getLong(SIZE_OF_LONG);

            if (lockExpired(lockExpirationTime))
            {
                // TODO: would be nicer to have a consumable channel for timed-out timers
                //   that we can stop consuming/yield on backpressure

                final boolean success = streamWriter.tryWriteTaskEvent(eventPosition, TaskState.EXPIRE_LOCK);
                if (!success)
                {
                    return;
                }
            }
        }
    }

    private boolean lockExpired(long lockExpirationTime)
    {
        return lockExpirationTime <= ActorClock.currentTimeMillis();
    }

    public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment environment)
    {
        final TypedEventProcessor<TaskEvent> registerTask = new TypedEventProcessor<TaskEvent>()
        {
            @Override
            public void updateState(TypedEvent<TaskEvent> event)
            {
                final long lockTime = event.getValue().getLockTime();

                mapAccessBuffer.putLong(0, event.getPosition());
                mapAccessBuffer.putLong(SIZE_OF_LONG, lockTime);

                expirationMap.put(event.getKey(), mapAccessBuffer);
            }
        };

        final TypedEventProcessor<TaskEvent> unregisterTask = new TypedEventProcessor<TaskEvent>()
        {
            @Override
            public void updateState(TypedEvent<TaskEvent> event)
            {
                expirationMap.remove(event.getKey());
            }
        };

        return environment.newStreamProcessor()
            .onEvent(EventType.TASK_EVENT, TaskState.LOCKED, registerTask)
            .onEvent(EventType.TASK_EVENT, TaskState.LOCK_EXPIRED, unregisterTask)
            .onEvent(EventType.TASK_EVENT, TaskState.COMPLETED, unregisterTask)
            .onEvent(EventType.TASK_EVENT, TaskState.FAILED, unregisterTask)
            .withListener(this)
            .withStateResource(expirationMap)
            .build();
    }
}
