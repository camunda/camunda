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
package org.camunda.tngp.client.event.impl.dto;

import java.time.Instant;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.event.TaskInstanceEvent;

public class TaskInstanceEventImpl implements TaskInstanceEvent
{
    public static final int STATE_UNKNOWN = -1;

    private long position;

    private DirectBuffer rawBuffer;

    private long id;

    private Long workflowInstanceId;

    private String type;

    private Instant lockExpirationTime;

    private Long lockOwnerId;

    private int state = STATE_UNKNOWN;

    @Override
    public long getPosition()
    {
        return position;
    }

    @Override
    public DirectBuffer getRawBuffer()
    {
        return rawBuffer;
    }

    @Override
    public String getType()
    {
        return type;
    }

    @Override
    public long getId()
    {
        return id;
    }

    @Override
    public Long getWorkflowInstanceId()
    {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(Long workflowInstanceId)
    {
        this.workflowInstanceId = workflowInstanceId;
    }

    @Override
    public Instant getLockExpirationTime()
    {
        return lockExpirationTime;
    }

    public void setLockExpirationTime(Instant lockExpirationTime)
    {
        this.lockExpirationTime = lockExpirationTime;
    }

    @Override
    public Long getLockOwnerId()
    {
        return lockOwnerId;
    }

    public void setLockOwnerId(long lockOwnerId)
    {
        this.lockOwnerId = lockOwnerId;
    }

    public void setPosition(long position)
    {
        this.position = position;
    }

    public void setRawBuffer(DirectBuffer rawBuffer)
    {
        this.rawBuffer = rawBuffer;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    @Override
    public boolean isNew()
    {
        return state == STATE_NEW;
    }

    @Override
    public boolean isLocked()
    {
        return state == STATE_LOCKED;
    }

    @Override
    public boolean isCompleted()
    {
        return state == STATE_COMPLETED;
    }

    public void setState(int state)
    {
        this.state = state;
    }

    @Override
    public int getState()
    {
        return state;
    }

}
