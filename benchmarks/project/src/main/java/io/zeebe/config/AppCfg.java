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

public class AppCfg {

  private String brokerUrl;
  private boolean tls;
  private int monitoringPort;
  private StarterCfg starter;
  private WorkerCfg worker;

  public String getBrokerUrl() {
    return brokerUrl;
  }

  public void setBrokerUrl(String brokerUrl) {
    this.brokerUrl = brokerUrl;
  }

  public boolean isTls() {
    return tls;
  }

  public void setTls(final boolean tls) {
    this.tls = tls;
  }

  public StarterCfg getStarter() {
    return starter;
  }

  public void setStarter(StarterCfg starter) {
    this.starter = starter;
  }

  public WorkerCfg getWorker() {
    return worker;
  }

  public void setWorker(WorkerCfg worker) {
    this.worker = worker;
  }

  public int getMonitoringPort() {
    return monitoringPort;
  }

  public void setMonitoringPort(int monitoringPort) {
    this.monitoringPort = monitoringPort;
  }
}
