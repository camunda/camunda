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
package org.camunda.tngp.client.event.impl.builder;

import static org.camunda.tngp.util.buffer.BufferUtil.bufferAsString;

import java.time.Instant;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.event.TaskInstanceEvent;
import org.camunda.tngp.client.event.impl.dto.TaskInstanceEventImpl;
import org.camunda.tngp.protocol.log.TaskInstanceEncoder;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;

public class TaskInstanceEventBuilder implements EventBuilder
{
    protected final TaskInstanceReader reader = new TaskInstanceReader();

    @Override
    public TaskInstanceEvent build(long position, DirectBuffer buffer)
    {
        reader.wrap(buffer, 0, buffer.capacity());

        final TaskInstanceEventImpl event = new TaskInstanceEventImpl();

        event.setPosition(position);
        event.setRawBuffer(buffer);

        event.setId(reader.id());
        event.setType(bufferAsString(reader.getTaskType()));

        event.setState(mapState(reader.state()));

        final long wfInstanceId = reader.wfInstanceId();
        if (wfInstanceId != TaskInstanceEncoder.wfInstanceIdNullValue())
        {
            event.setWorkflowInstanceId(wfInstanceId);
        }

        final long lockTime = reader.lockTime();
        if (lockTime != TaskInstanceEncoder.lockTimeNullValue())
        {
            event.setLockExpirationTime(Instant.ofEpochMilli(lockTime));
        }

        final long lockOwnerId = reader.lockOwnerId();
        if (lockOwnerId != TaskInstanceEncoder.lockOwnerIdNullValue())
        {
            event.setLockOwnerId(lockOwnerId);
        }

        return event;
    }

    protected int mapState(TaskInstanceState state)
    {
        switch (state)
        {
            case NEW:
                return TaskInstanceEvent.STATE_NEW;
            case LOCKED:
                return TaskInstanceEvent.STATE_LOCKED;
            case COMPLETED:
                return TaskInstanceEvent.STATE_COMPLETED;
            default:
                return TaskInstanceEventImpl.STATE_UNKNOWN;
        }
    }

}
