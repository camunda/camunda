/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.taskqueue.processor;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;

import java.io.Serializable;
import java.util.HashMap;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorCommand;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.logstreams.snapshot.SerializableWrapper;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.util.time.ClockUtil;

public class TaskExpireLockStreamProcessor implements StreamProcessor
{
    protected static final int INDEX_VALUE_LENGTH = SIZE_OF_INT + SIZE_OF_INT;

    protected final EventProcessor lockedEventProcessor = new LockedEventProcessor();
    protected final EventProcessor unlockEventProcessor = new UnlockEventProcessor();
    protected final EventProcessor expireLockEventProcessor = new ExpireLockEventProcessor();

    protected final StreamProcessorCommand checkLockExpirationCmd = new CheckLockExpirationCmd();

    // TODO #161 - replace the index by a more efficient one
    protected HashMap<Long, Bucket> index = new HashMap<>();
    protected SnapshotSupport indexSnapshot = new SerializableWrapper<>(index);

    class Bucket implements Serializable
    {
        private static final long serialVersionUID = 1L;

        public long eventPosition;
        public long expirationTime;
    }

    protected ManyToOneConcurrentArrayQueue<StreamProcessorCommand> cmdQueue;

    protected LogStreamReader targetLogStreamReader;
    protected LogStreamWriter targetLogStreamWriter;

    protected int targetLogStreamId;
    protected int streamProcessorId;

    protected final BrokerEventMetadata targetEventMetadata = new BrokerEventMetadata();
    protected final TaskEvent taskEvent = new TaskEvent();
    protected long eventKey = 0;
    protected long eventPosition = 0;

    protected long lastWrittenEventPosition = 0;

    @Override
    public SnapshotSupport getStateResource()
    {
        return indexSnapshot;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        streamProcessorId = context.getId();
        cmdQueue = context.getStreamProcessorCmdQueue();
        targetLogStreamReader = context.getTargetLogStreamReader();
        targetLogStreamWriter = context.getLogStreamWriter();
        targetLogStreamId = context.getTargetStream().getId();
    }

    public static MetadataFilter eventFilter()
    {
        return (m) -> m.getEventType() == EventType.TASK_EVENT;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        eventKey = event.getLongKey();
        eventPosition = event.getPosition();

        taskEvent.reset();
        event.readValue(taskEvent);

        EventProcessor eventProcessor = null;

        switch (taskEvent.getEventType())
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

        @Override
        public void processEvent()
        {
            // just add event to index
        }

        @Override
        public void updateState()
        {
            final Bucket bucket = new Bucket();
            bucket.eventPosition = eventPosition;
            bucket.expirationTime = taskEvent.getLockTime();

            index.put(eventKey, bucket);
        }

    }

    class UnlockEventProcessor implements EventProcessor
    {

        @Override
        public void processEvent()
        {
            // just remove event from index
        }

        @Override
        public void updateState()
        {
            index.remove(eventKey);
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
        cmdQueue.add(checkLockExpirationCmd);
    }

    class CheckLockExpirationCmd implements StreamProcessorCommand
    {

        @Override
        public void execute()
        {
            if (index.size() > 0)
            {
                for (long eventKey : index.keySet())
                {
                    final Bucket bucket = index.get(eventKey);

                    final long eventPosition = bucket.eventPosition;
                    final long lockExpirationTime = bucket.expirationTime;

                    checkLockExpirationTime(eventKey, eventPosition, lockExpirationTime);
                }
            }
        }

        protected void checkLockExpirationTime(long eventKey, final long eventPosition, final long lockExpirationTime)
        {
            if (lockExpirationTime <= ClockUtil.getCurrentTimeInMillis())
            {
                final boolean found = targetLogStreamReader.seek(eventPosition);
                if (found && targetLogStreamReader.hasNext())
                {
                    final LoggedEvent lockedEvent = targetLogStreamReader.next();

                    writeLockExpireEvent(eventKey, lockedEvent);
                }
                else
                {
                    throw new IllegalStateException("Failed to check the task lock expiration time. Indexed task event not found in log stream.");
                }
            }
        }

        protected void writeLockExpireEvent(long eventKey, final LoggedEvent lockedEvent)
        {
            taskEvent.reset();
            lockedEvent.readValue(taskEvent);

            taskEvent.setEventType(TaskEventType.EXPIRE_LOCK);

            targetEventMetadata
                .reset()
                .protocolVersion(Constants.PROTOCOL_VERSION)
                .eventType(TASK_EVENT);

            // TODO: targetEventMetadata.raftTermId(raftTermId);

            final long position = targetLogStreamWriter
                    .producerId(streamProcessorId)
                    .sourceEvent(targetLogStreamId, eventPosition)
                    .key(eventKey)
                    .metadataWriter(targetEventMetadata)
                    .valueWriter(taskEvent)
                    .tryWrite();

            if (position >= 0)
            {
                lastWrittenEventPosition = position;

                index.remove(eventKey);
            }
        }
    }

}
