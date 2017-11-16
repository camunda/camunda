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

import static io.zeebe.protocol.clientapi.EventType.TASK_EVENT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.zeebe.broker.logstreams.processor.MetadataFilter;
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
import io.zeebe.map.ZbMapIterator;
import io.zeebe.map.iterator.Long2BytesZbMapEntry;
import io.zeebe.map.types.ByteArrayValueHandler;
import io.zeebe.map.types.LongKeyHandler;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.time.ClockUtil;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class TaskExpireLockStreamProcessor implements StreamProcessor
{
    protected static final int MAP_VALUE_MAX_LENGTH = SIZE_OF_LONG + SIZE_OF_LONG;

    protected final EventProcessor lockedEventProcessor = new LockedEventProcessor();
    protected final EventProcessor unlockEventProcessor = new UnlockEventProcessor();
    protected final EventProcessor expireLockEventProcessor = new ExpireLockEventProcessor();

    protected final Runnable checkLockExpirationCmd = new CheckLockExpirationCmd();

    protected Long2BytesZbMap expirationMap = new Long2BytesZbMap(MAP_VALUE_MAX_LENGTH);
    protected ZbMapSnapshotSupport<Long2BytesZbMap> mapSnapshotSupport = new ZbMapSnapshotSupport<>(expirationMap);

    protected DeferredCommandContext cmdQueue;

    protected LogStreamReader targetLogStreamReader;
    protected LogStreamWriter targetLogStreamWriter;

    protected LogStream targetStream;
    protected int targetLogStreamPartitionId;
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
        cmdQueue = context.getStreamProcessorCmdQueue();
        targetLogStreamReader = context.getTargetLogStreamReader();
        targetLogStreamWriter = context.getLogStreamWriter();

        targetStream = context.getTargetStream();
        targetLogStreamPartitionId = targetStream.getPartitionId();

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

    public void checkLockExpirationAsync()
    {
        cmdQueue.runAsync(checkLockExpirationCmd);
    }

    class CheckLockExpirationCmd implements Runnable
    {
        private final Long2BytesZbMapEntry entry = new Long2BytesZbMapEntry();
        private final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

        private int entryIndex = 0;
        private final ExpandableArrayBuffer toRemoveEntries = new ExpandableArrayBuffer(1024 * SIZE_OF_LONG);

        @Override
        public void run()
        {
            final ZbMapIterator<LongKeyHandler, ByteArrayValueHandler, Long2BytesZbMapEntry> iterator = new ZbMapIterator<LongKeyHandler, ByteArrayValueHandler, Long2BytesZbMapEntry>(expirationMap, entry);

            while (iterator.hasNext())
            {
                iterator.next();

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
                        toRemoveEntries.putLong(entryIndex++, eventKey);
                    }
                }
            }

            // iterate over the entries which should be removed
            for (int i = 0; i < entryIndex; i++)
            {
                final long eventKey = toRemoveEntries.getLong(0);
                if (eventKey > 0)
                {
                    expirationMap.remove(eventKey);
                }
            }

            // reset
            toRemoveEntries.setMemory(0, toRemoveEntries.capacity(), (byte) 0);
            entryIndex = 0;
        }

        protected boolean lockExpired(long lockExpirationTime)
        {
            return lockExpirationTime <= ClockUtil.getCurrentTimeInMillis();
        }

        protected LoggedEvent findEvent(long position)
        {
            final boolean found = targetLogStreamReader.seek(position);

            if (found && targetLogStreamReader.hasNext())
            {
                return targetLogStreamReader.next();
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

            final long position = targetLogStreamWriter
                    .producerId(streamProcessorId)
                    .sourceEvent(targetLogStreamPartitionId, lockedEvent.getPosition())
                    .key(eventKey)
                    .metadataWriter(targetEventMetadata)
                    .valueWriter(taskEvent)
                    .tryWrite();

            return position;
        }
    }

}
