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
package io.zeebe.broker.job.processor;

import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import org.agrona.DirectBuffer;

public class JobSubscription
{
    public static final int LOCK_OWNER_MAX_LENGTH = 64;

    private final int partitionId;

    private final DirectBuffer lockJobType;

    private final long lockDuration;
    private final DirectBuffer lockOwner;

    private final int streamId;

    private long subscriberKey;

    private int credits;

    public JobSubscription(int partitionId, DirectBuffer lockJobType, long lockDuration, DirectBuffer lockOwner, int streamId)
    {
        this.partitionId = partitionId;
        this.lockJobType = cloneBuffer(lockJobType);
        this.lockDuration = lockDuration;
        this.lockOwner = cloneBuffer(lockOwner);
        this.streamId = streamId;
    }

    public int getCredits()
    {
        return credits;
    }

    public void setCredits(int credits)
    {
        this.credits = credits;
    }

    public long getSubscriberKey()
    {
        return subscriberKey;
    }

    public DirectBuffer getLockJobType()
    {
        return lockJobType;
    }

    public long getLockDuration()
    {
        return lockDuration;
    }

    public DirectBuffer getLockOwner()
    {
        return lockOwner;
    }

    public int getStreamId()
    {
        return streamId;
    }

    public void setSubscriberKey(long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

}
