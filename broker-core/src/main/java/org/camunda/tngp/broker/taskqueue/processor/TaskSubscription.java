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

import org.agrona.DirectBuffer;

public class TaskSubscription
{
    protected long id;
    protected int channelId;

    protected long topicId;

    protected DirectBuffer taskType;
    protected long lockDuration;

    protected int credits;

    public TaskSubscription setId(long id)
    {
        this.id = id;
        return this;
    }

    public TaskSubscription setChannelId(int channelId)
    {
        this.channelId = channelId;
        return this;
    }

    public TaskSubscription setTopicId(long topicId)
    {
        this.topicId = topicId;
        return this;
    }

    public TaskSubscription setTaskType(DirectBuffer taskType)
    {
        this.taskType = taskType;
        return this;
    }

    public TaskSubscription setLockDuration(long lockDuration)
    {
        this.lockDuration = lockDuration;
        return this;
    }

    public TaskSubscription setCredits(int credits)
    {
        this.credits = credits;
        return this;
    }

    public long getId()
    {
        return id;
    }

    public DirectBuffer getLockTaskType()
    {
        return taskType;
    }

    public long getLockTime()
    {
        return lockDuration;
    }

    public int getCredits()
    {
        return credits;
    }

    public int getChannelId()
    {
        return channelId;
    }

    public long getTopicId()
    {
        return topicId;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("TaskSubscription [id=");
        builder.append(id);
        builder.append(", channelId=");
        builder.append(channelId);
        builder.append(", topicId=");
        builder.append(topicId);
        builder.append(", taskType=");
        builder.append(taskType);
        builder.append(", lockDuration=");
        builder.append(lockDuration);
        builder.append(", credits=");
        builder.append(credits);
        builder.append("]");
        return builder.toString();
    }

}
