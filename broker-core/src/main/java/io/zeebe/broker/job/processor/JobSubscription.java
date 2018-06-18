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

public class JobSubscription {
  public static final int WORKER_MAX_LENGTH = 64;

  private final int partitionId;

  private final DirectBuffer jobType;

  private final long timeout;
  private final DirectBuffer worker;

  private final int streamId;

  private long subscriberKey;

  private int credits;

  public JobSubscription(
      int partitionId, DirectBuffer jobType, long timeout, DirectBuffer worker, int streamId) {
    this.partitionId = partitionId;
    this.jobType = cloneBuffer(jobType);
    this.timeout = timeout;
    this.worker = cloneBuffer(worker);
    this.streamId = streamId;
  }

  public int getCredits() {
    return credits;
  }

  public void setCredits(int credits) {
    this.credits = credits;
  }

  public long getSubscriberKey() {
    return subscriberKey;
  }

  public DirectBuffer getJobType() {
    return jobType;
  }

  public long getTimeout() {
    return timeout;
  }

  public DirectBuffer getWorker() {
    return worker;
  }

  public int getStreamId() {
    return streamId;
  }

  public void setSubscriberKey(long subscriberKey) {
    this.subscriberKey = subscriberKey;
  }

  public int getPartitionId() {
    return partitionId;
  }
}
