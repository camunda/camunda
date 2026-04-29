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
package io.camunda.zeebe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "load-tester")
public class LoadTesterProperties {

  @NestedConfigurationProperty private StarterProperties starter = new StarterProperties();

  @NestedConfigurationProperty private WorkerProperties worker = new WorkerProperties();

  public StarterProperties getStarter() {
    return starter;
  }

  public void setStarter(final StarterProperties starter) {
    this.starter = starter;
  }

  public WorkerProperties getWorker() {
    return worker;
  }

  public void setWorker(final WorkerProperties worker) {
    this.worker = worker;
  }
}
