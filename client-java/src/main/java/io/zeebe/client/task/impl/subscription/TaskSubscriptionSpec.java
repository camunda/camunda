/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.task.impl.subscription;

import io.zeebe.client.task.TaskHandler;

public class TaskSubscriptionSpec
{

    protected final String topic;
    protected final TaskHandler taskHandler;
    protected final String taskType;
    protected final long lockTime;
    protected final String lockOwner;
    protected final int capacity;

    public TaskSubscriptionSpec(
            String topic,
            TaskHandler taskHandler,
            String taskType,
            long lockTime,
            String lockOwner,
            int capacity)
    {
        this.topic = topic;
        this.taskHandler = taskHandler;
        this.taskType = taskType;
        this.lockTime = lockTime;
        this.lockOwner = lockOwner;
        this.capacity = capacity;
    }

    public String getTopic()
    {
        return topic;
    }

    public TaskHandler getTaskHandler()
    {
        return taskHandler;
    }

    public String getTaskType()
    {
        return taskType;
    }

    public long getLockTime()
    {
        return lockTime;
    }

    public String getLockOwner()
    {
        return lockOwner;
    }

    public int getCapacity()
    {
        return capacity;
    }

    public boolean isManaged()
    {
        return taskHandler != null;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("[topic=");
        builder.append(topic);
        builder.append(", taskHandler=");
        builder.append(taskHandler);
        builder.append(", taskType=");
        builder.append(taskType);
        builder.append(", lockTime=");
        builder.append(lockTime);
        builder.append(", lockOwner=");
        builder.append(lockOwner);
        builder.append(", capacity=");
        builder.append(capacity);
        builder.append("]");
        return builder.toString();
    }



}
