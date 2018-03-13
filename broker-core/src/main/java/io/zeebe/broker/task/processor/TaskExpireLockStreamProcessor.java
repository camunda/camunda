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

import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.task.TaskQueueManagerService;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.map.Long2BytesZbMap;
import io.zeebe.map.iterator.Long2BytesZbMapEntry;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.sched.clock.ActorClock;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.Iterator;

import static io.zeebe.protocol.clientapi.EventType.TASK_EVENT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

public class TaskExpireLockStreamProcessor implements StreamProcessor
{
    protected static final int MAP_VALUE_MAX_LENGTH = SIZE_OF_LONG + SIZE_OF_LONG;

    protected final EventProcessor lockedEventProcessor = new LockedEventProcessor();
    protected final EventProcessor unlockEventProcessor = new UnlockEventProcessor();
    protected final EventProcessor expireLockEventProcessor = new ExpireLockEventProcessor();

    protected final Runnable checkLockExpirationCmd = new CheckLockExpirationCmd();

    protected Long2BytesZbMap expirationMap = new Long2BytesZbMap(MAP_VALUE_MAX_LENGTH);
    protected ZbMapSnapshotSupport<Long2BytesZbMap> mapSnapshotSupport = new ZbMapSnapshotSupport<>(expirationMap);

    protected LogStreamReader logStreamReader;
    protected LogStreamWriter logStreamWriter;

    protected LogStream logStream;
    protected int logStreamPartitionId;
    protected int streamProcessorId;

    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();
    protected final TaskEvent taskEvent = new TaskEvent();
    protected long eventKey = 0;
    protected long eventPosition = 0;

    protected long lastWrittenEventPosition = 0;

    @Override
    public SnapshotSupport getStateResource()
    {
        return mapSnapshotSupport;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        streamProcessorId = context.getId();

        context.getActorControl().runAtFixedRate(TaskQueueManagerService.LOCK_EXPIRATION_INTERVAL, checkLockExpirationCmd);
        logStreamReader = context.getLogStreamReader();
        logStreamWriter = context.getLogStreamWriter();

        logStream = context.getLogStream();
        logStreamPartitionId = logStream.getPartitionId();

        // restore map from snapshot
        expirationMap = mapSnapshotSupport.getZbMap();
    }

    @Override
    public void onClose()
    {
        expirationMap.close();
    }

    public static MetadataFilter eventFilter()
    {
        return (m) -> m.getEventType() == EventType.TASK_EVENT;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        eventKey = event.getKey();
        eventPosition = event.getPosition();

        taskEvent.reset();
        event.readValue(taskEvent);

        EventProcessor eventProcessor = null;

        switch (taskEvent.getState())
        {
            case LOCKED:
                eventProcessor = lockedEventProcessor;
                break;
            case EXPIRE_LOCK:
                eventProcessor = expireLockEventProcessor;
                break;
            case LOCK_EXPIRED:
            case COMPLETED:
            case FAILED:
                eventProcessor = unlockEventProcessor;
                break;

            default:
                break;
        }
        return eventProcessor;
    }

    class LockedEventProcessor implements EventProcessor
    {
        UnsafeBuffer buffer = new UnsafeBuffer(new byte[MAP_VALUE_MAX_LENGTH]);

        @Override
        public void processEvent()
        {
            // just add event to map
        }

        @Override
        public void updateState()
        {
            buffer.putLong(0, eventPosition);
            buffer.putLong(SIZE_OF_LONG, taskEvent.getLockTime());

            expirationMap.put(eventKey, buffer);
        }

    }

    class UnlockEventProcessor implements EventProcessor
    {

        @Override
        public void processEvent()
        {
            // just remove event from map
        }

        @Override
        public void updateState()
        {
            expirationMap.remove(eventKey);
        }

    }

    class ExpireLockEventProcessor implements EventProcessor
    {

        @Override
        public void processEvent()
        {
            // just process the previous written event for writing a snapshot afterwards
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            // returns the position of the previous written event
            return lastWrittenEventPosition;
        }

    }

    class CheckLockExpirationCmd implements Runnable
    {
        private final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);
        private final ExpandableArrayBuffer toRemoveEntries = new ExpandableArrayBuffer(1024 * SIZE_OF_LONG);

        private int entryIndex = 0;

        @Override
        public void run()
        {
            final Iterator<Long2BytesZbMapEntry> iterator = expirationMap.iterator();

            while (iterator.hasNext())
            {
                final Long2BytesZbMapEntry entry = iterator.next();

                final long eventKey = entry.getKey();
                final DirectBuffer value = entry.getValue();
                buffer.wrap(value);

                final long eventPosition = buffer.getLong(0);
                final long lockExpirationTime = buffer.getLong(SIZE_OF_LONG);

                if (lockExpired(lockExpirationTime))
                {
                    final LoggedEvent taskLockedEvent = findEvent(eventPosition);
                    final long position = writeLockExpireEvent(eventKey, taskLockedEvent);
                    final boolean successfulWritten = position >= 0;
                    if (successfulWritten)
                    {
                        lastWrittenEventPosition = position;
                        // add to remove entries
                        toRemoveEntries.putLong(entryIndex * SIZE_OF_LONG, eventKey);
                        entryIndex++;
                    }
                }
            }

            // iterate over the entries which should be removed
            for (int i = 0; i < entryIndex; i++)
            {
                final long eventKey = toRemoveEntries.getLong(i * SIZE_OF_LONG);
                if (eventKey > 0)
                {
                    expirationMap.remove(eventKey);
                }
            }

            // reset
            entryIndex = 0;
        }

        protected boolean lockExpired(long lockExpirationTime)
        {
            return lockExpirationTime <= ActorClock.currentTimeMillis();
        }

        protected LoggedEvent findEvent(long position)
        {
            final boolean found = logStreamReader.seek(position);

            if (found && logStreamReader.hasNext())
            {
                return logStreamReader.next();
            }
            else
            {
                throw new IllegalStateException("Failed to check the task lock expiration time. Indexed task event not found in log stream.");
            }
        }

        protected long writeLockExpireEvent(long eventKey, final LoggedEvent lockedEvent)
        {
            taskEvent.reset();
            lockedEvent.readValue(taskEvent);

            taskEvent.setState(TaskState.EXPIRE_LOCK);

            targetEventMetadata
                .reset()
                .protocolVersion(Protocol.PROTOCOL_VERSION)
                .eventType(TASK_EVENT);

            final long position = logStreamWriter
                    .producerId(streamProcessorId)
                    .sourceEvent(logStreamPartitionId, lockedEvent.getPosition())
                    .key(eventKey)
                    .metadataWriter(targetEventMetadata)
                    .valueWriter(taskEvent)
                    .tryWrite();

            return position;
        }
    }

}
