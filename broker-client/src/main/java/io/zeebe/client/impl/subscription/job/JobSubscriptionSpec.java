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
package io.zeebe.client.impl.subscription.job;

import io.zeebe.client.api.subscription.JobHandler;

public class JobSubscriptionSpec {

  protected final String topic;
  protected final JobHandler jobHandler;
  protected final String jobType;
  protected final long timeout;
  protected final String worker;
  protected final int capacity;

  public JobSubscriptionSpec(
      String topic,
      JobHandler jobHandler,
      String taskType,
      long timeout,
      String worker,
      int capacity) {
    this.topic = topic;
    this.jobHandler = jobHandler;
    this.jobType = taskType;
    this.timeout = timeout;
    this.worker = worker;
    this.capacity = capacity;
  }

  public String getTopic() {
    return topic;
  }

  public JobHandler getJobHandler() {
    return jobHandler;
  }

  public String getJobType() {
    return jobType;
  }

  public long getTimeout() {
    return timeout;
  }

  public String getWorker() {
    return worker;
  }

  public int getCapacity() {
    return capacity;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("[topic=");
    builder.append(topic);
    builder.append(", jobHandler=");
    builder.append(jobHandler);
    builder.append(", jobType=");
    builder.append(jobType);
    builder.append(", lockTime=");
    builder.append(timeout);
    builder.append(", lockOwner=");
    builder.append(worker);
    builder.append(", capacity=");
    builder.append(capacity);
    builder.append("]");
    return builder.toString();
  }
}
