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
package io.zeebe.config;

import java.time.Duration;

public class WorkerCfg {

  private String jobType;
  private String workerName;

  private int threads;

  private int capacity;
  private Duration pollingDelay;
  private Duration completionDelay;
  private boolean completeJobsAsync;

  private String payloadPath;

  public String getJobType() {
    return jobType;
  }

  public void setJobType(String jobType) {
    this.jobType = jobType;
  }

  public String getWorkerName() {
    return workerName;
  }

  public void setWorkerName(String workerName) {
    this.workerName = workerName;
  }

  public int getThreads() {
    return threads;
  }

  public void setThreads(int threads) {
    this.threads = threads;
  }

  public int getCapacity() {
    return capacity;
  }

  public void setCapacity(int capacity) {
    this.capacity = capacity;
  }

  public Duration getPollingDelay() {
    return pollingDelay;
  }

  public void setPollingDelay(Duration pollingDelay) {
    this.pollingDelay = pollingDelay;
  }

  public Duration getCompletionDelay() {
    return completionDelay;
  }

  public void setCompletionDelay(Duration completionDelay) {
    this.completionDelay = completionDelay;
  }

  public String getPayloadPath() {
    return payloadPath;
  }

  public void setPayloadPath(String payloadPath) {
    this.payloadPath = payloadPath;
  }

  public boolean isCompleteJobsAsync() {
    return completeJobsAsync;
  }

  public void setCompleteJobsAsync(boolean completeJobsAsync) {
    this.completeJobsAsync = completeJobsAsync;
  }
}
