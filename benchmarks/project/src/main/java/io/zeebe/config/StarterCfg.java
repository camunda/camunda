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

public class StarterCfg {

  private String processId;
  private int rate;
  private int threads;

  /** Paths are relative to classpath. */
  private String bpmnXmlPath;

  private String payloadPath;
  private boolean withResults;
  private Duration withResultsTimeout;

  private int durationLimit;

  public String getProcessId() {
    return processId;
  }

  public void setProcessId(String processId) {
    this.processId = processId;
  }

  public int getRate() {
    return rate;
  }

  public void setRate(int rate) {
    this.rate = rate;
  }

  public int getThreads() {
    return threads;
  }

  public void setThreads(int threads) {
    this.threads = threads;
  }

  public String getBpmnXmlPath() {
    return bpmnXmlPath;
  }

  public void setBpmnXmlPath(String bpmnXmlPath) {
    this.bpmnXmlPath = bpmnXmlPath;
  }

  public String getPayloadPath() {
    return payloadPath;
  }

  public void setPayloadPath(String payloadPath) {
    this.payloadPath = payloadPath;
  }

  public boolean isWithResults() {
    return this.withResults;
  }

  public void setWithResults(boolean withResults) {
    this.withResults = withResults;
  }

  public Duration getWithResultsTimeout() {
    return withResultsTimeout;
  }

  public void setWithResultsTimeout(Duration withResultsTimeout) {
    this.withResultsTimeout = withResultsTimeout;
  }

  public int getDurationLimit() {
    return durationLimit;
  }

  public void setDurationLimit(int durationLimit) {
    this.durationLimit = durationLimit;
  }
}
