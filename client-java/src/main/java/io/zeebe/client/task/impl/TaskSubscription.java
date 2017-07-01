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
package io.zeebe.client.task.impl;

public class TaskSubscription
{
    private long subscriberKey;

    private String topicName;
    private int partitionId;
    private String taskType;

    private long lockDuration;
    private String lockOwner;
    private int credits;

    public long getSubscriberKey()
    {
        return subscriberKey;
    }

    public void setSubscriberKey(final long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
    }

    public String getTopicName()
    {
        return topicName;
    }

    public void setTopicName(final String topicName)
    {
        this.topicName = topicName;
    }

    public long getPartitionId()
    {
        return partitionId;
    }

    public void setPartitionId(final int partitionId)
    {
        this.partitionId = partitionId;
    }

    public String getTaskType()
    {
        return taskType;
    }

    public void setTaskType(final String taskType)
    {
        this.taskType = taskType;
    }

    public long getLockDuration()
    {
        return lockDuration;
    }

    public void setLockDuration(final long lockDuration)
    {
        this.lockDuration = lockDuration;
    }

    public int getCredits()
    {
        return credits;
    }

    public void setCredits(final int credits)
    {
        this.credits = credits;
    }

    public String getLockOwner()
    {
        return lockOwner;
    }

    public void setLockOwner(final String lockOwner)
    {
        this.lockOwner = lockOwner;
    }

}
